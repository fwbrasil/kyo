package kyo.concurrent.scheduler

import kyo.concurrent.scheduler.IOTask
import kyo.ios.Preempt

import java.util.Comparator
import java.util.PriorityQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.LockSupport

private final class Worker(r: Runnable)
    extends Thread(r) {

  private val queue = Queue[IOTask[_]]()

  @volatile private var running                = false
  @volatile private var currentTask: IOTask[_] = null
  @volatile private var parkedThread: Thread   = null

  private val delay = MovingStdDev(7)

  def park() =
    parkedThread = this
    LockSupport.parkNanos(this, 100_000_000L)
    parkedThread = null

  def steal(w: Worker): IOTask[_] =
    queue.steal(w.queue)

  def enqueueLocal(t: IOTask[_]): Boolean =
    queue.offer(t)

  def enqueue(t: IOTask[_]): Boolean =
    isAvailable() && queue.offer(t) && {
      LockSupport.unpark(parkedThread)
      true
    }

  def isAvailable(): Boolean =
    running && {
      val t = currentTask
      (t == null || !t())
    }

  def cycle(): Unit =
    val t = currentTask
    if (t != null && !queue.isEmpty()) {
      t.preempt()
    }

  def flush(): Unit =
    queue.drain(Scheduler.submit)

  def load(): Int =
    var s = queue.size()
    if (currentTask != null)
      s += 1
    s

  def runWorker(init: IOTask[_]) =
    var task = init
    def stop() =
      !running || {
        val stop = Scheduler.stopWorker()
        if (stop) {
          running = false
        }
        stop
      }
    running = true
    Scheduler.workers.add(this)
    while (!stop()) {
      if (task == null) {
        task = queue.poll()
      }
      if (task != null) {
        currentTask = task
        task.run()
        currentTask = null
        if (task.reenqueue()) {
          task = queue.addAndPoll(task)
        } else {
          delay.observe(task.delay())
          task = null
        }
      } else {
        task = Scheduler.steal(this)
        if (task == null) {
          Scheduler.idle(this)
        }
      }
    }
    Scheduler.workers.remove(this)
    running = false
    if (task != null) {
      queue.add(task)
    }
    flush()

  override def toString =
    s"Worker(thread=${getName},load=${load()},delay=${delay.avg()},task=$currentTask,queue.size=${queue.size()},frame=${this.getStackTrace()(0)})"
}

private object Worker {
  def apply(): Worker =
    Thread.currentThread() match {
      case w: Worker => w
      case _         => null
    }
}
