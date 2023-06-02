package kyo.concurrent.scheduler

import kyo.concurrent.fibers._
import kyo._
import kyo.core._
import kyo.core.internal._
import kyo.ios._
import kyo.locals._
import kyo.resources._

import scala.annotation.tailrec
import scala.util.control.NonFatal
import org.jctools.queues.MpmcArrayQueue
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.Arrays

private[kyo] object IOTask {
  private def nullIO[T] = null.asInstanceOf[T > IOs]
  /*inline(2)*/
  def apply[T](
      /*inline(2)*/ v: T > (IOs with Fibers),
      st: Locals.State,
      ensures: Any /*(() => Unit) | ArrayDeque[() => Unit]*/ = null,
      runtime: Int = 0
  ): IOTask[T] = {
    val f = new IOTask[T](v, st, ensures, runtime)
    Scheduler.schedule(f)
    f
  }

  private val bufferCache = new MpmcArrayQueue[ArrayDeque[() => Unit]](1000)
  private def buffer(): ArrayDeque[() => Unit] = {
    val b = bufferCache.poll()
    if (b == null) new ArrayDeque()
    else b
  }

  private var token = 0
  private def avoidUnstableIf(): Boolean =
    token < 20000 && {
      token += 1
      token % 2 == 0
    }

  object TaskOrdering extends Ordering[IOTask[_]] {
    override def lt(x: IOTask[_], y: IOTask[_]): Boolean = {
      val r = x.runtime
      r == 0 || r < y.runtime
    }
    def compare(x: IOTask[_], y: IOTask[_]): Int =
      y.runtime - x.runtime
  }

  implicit def ord: Ordering[IOTask[_]] = TaskOrdering
}

private[kyo] final class IOTask[T](
    private var curr: T > (IOs with Fibers),
    private val st: Locals.State,
    private var ensures: Any /*(() => Unit) | ArrayDeque[() => Unit]*/ = null,
    private var runtime: Int
) extends IOPromise[T]
    with Preempt {
  import IOTask._

  private val creationTs = Coordinator.tick()

  @volatile private var preempting = false

  def preempt() =
    preempting = true

  override protected def onComplete(): Unit =
    preempt()

  def apply(): Boolean =
    preempting

  @tailrec private def eval(start: Long, curr: T > (IOs with Fibers)): T > (IOs with Fibers) = {
    def finalize() = {
      ensures match {
        case null =>
        case f: (() => Unit) @unchecked =>
          f()
        case arr: ArrayDeque[() => Unit] @unchecked =>
          while (!arr.isEmpty()) {
            val f = arr.poll()
            f()
          }
          bufferCache.offer(arr)
      }
      ensures = null
    }
    if (preempting || avoidUnstableIf()) {
      if (isDone()) {
        finalize()
        nullIO
      } else {
        curr
      }
    } else {
      curr match {
        case kyo: Kyo[IO, IOs, Unit, T, IOs with Fibers] @unchecked if (kyo.effect eq IOs) =>
          eval(start, kyo((), this, st))
        case kyo: Kyo[Fiber, Fibers, Any, T, IOs with Fibers] @unchecked
            if (kyo.effect eq Fibers) =>
          kyo.value.state match {
            case promise: IOPromise[T] @unchecked =>
              this.interrupts(promise)
              runtime += (Coordinator.tick() - start).asInstanceOf[Int]
              promise.onComplete { (v: Any > IOs) =>
                val io = v.map(kyo(_, this.asInstanceOf[Safepoint[Fiber, Fibers]], st))
                this.become(IOTask(io, st, ensures, runtime))
              }
            case Failed(ex) =>
              complete(IOs.fail(ex))
            case v =>
              complete(v.asInstanceOf[T > IOs])
          }
          nullIO
        case _ =>
          complete(curr.asInstanceOf[T > IOs])
          finalize()
          nullIO
      }
    }
  }

  def run(): Unit = {
    val start = Coordinator.tick()
    try {
      curr = eval(start, curr)
      preempting = false
      if (curr != nullIO) {
        runtime += (Coordinator.tick() - start).asInstanceOf[Int]
      }
    } catch {
      case ex if (NonFatal(ex)) =>
        complete(IOs[T, Any](throw ex))
        curr = nullIO
    }
  }

  def reenqueue(): Boolean =
    curr != nullIO

  def delay() = Coordinator.tick() - creationTs - runtime

  def ensure(f: () => Unit): Unit =
    if (curr == nullIO) {
      f()
    } else {
      ensures match {
        case null =>
          ensures = f
        case f0: (() => Unit) @unchecked =>
          val b = buffer()
          b.add(f0)
          b.add(f)
          ensures = b
        case arr: ArrayDeque[() => Unit] @unchecked =>
          arr.add(f)
      }
    }

  def remove(f: () => Unit): Unit =
    ensures match {
      case null =>
      case f0: (() => Unit) @unchecked =>
        if (f0 eq f) ensures = null
      case arr: ArrayDeque[() => Unit] @unchecked =>
        def loop(): Unit =
          if (arr.remove(f)) loop()
        loop()
    }

  override final def toString = {
    val e = ensures match {
      case null =>
        "[]"
      case f: (() => Unit) @unchecked =>
        s"[$f]"
      case arr: ArrayDeque[() => Unit] @unchecked =>
        Arrays.toString(arr.toArray)
    }
    s"IOTask(id=${hashCode},preempting=$preempting,curr=$curr,ensures=$ensures,runtime=$runtime,state=$s)"
  }
}
