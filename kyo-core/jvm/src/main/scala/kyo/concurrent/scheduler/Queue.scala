package kyo.concurrent.scheduler

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable.PriorityQueue

private final class Queue[T](using Ordering[T]) extends AtomicBoolean {

  private val queue = PriorityQueue[T]()

  @volatile private var items = 0

  def isEmpty() =
    items == 0

  def size(): Int =
    items

  def add(t: T): Unit =
    modify {
      items += 1
      queue.addOne(t)
    }

  def offer(t: T): Boolean =
    tryModify {
      items += 1
      queue.addOne(t)
      true
    }

  def poll(): T =
    if (isEmpty()) {
      null.asInstanceOf[T]
    } else {
      modify {
        if (isEmpty())
          null.asInstanceOf[T]
        else {
          items -= 1
          queue.dequeue()
        }
      }
    }

  def addAndPoll(t: T): T =
    if (isEmpty()) {
      t
    } else {
      modify {
        if (isEmpty()) t
        else {
          val r = queue.dequeue()
          queue.addOne(t)
          r
        }
      }
    }

  def steal(to: Queue[T]): T =
    var t: T = null.asInstanceOf[T]
    !isEmpty() && tryModify {
      !isEmpty() && to.isEmpty() && to.tryModify {
        t = queue.dequeue()
        val s = size() - 1
        var i = s - (s / 2)
        items -= i + 1
        to.items += i
        while (i > 0) {
          to.queue.addOne(queue.dequeue())
          i -= 1
        }
        true
      }
    }
    t

  def drain(f: T => Unit): Unit =
    modify {
      items = 0
      queue.foreach(f)
      queue.clear()
    }

  private inline def modify[T](inline f: => T): T =
    while (!compareAndSet(false, true)) {}
    try f
    finally set(false)

  private inline def tryModify[T](inline f: => Boolean): Boolean =
    compareAndSet(false, true) && {
      try f
      finally set(false)
    }

  override def toString = modify { s"Queue(${queue.mkString(",")})" }
}
