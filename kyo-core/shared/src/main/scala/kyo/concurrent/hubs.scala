package kyo.concurrent

import kyo._
import kyo.ios._
import kyo.tries._
import kyo.concurrent.fibers._
import kyo.concurrent.channels._
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CopyOnWriteArraySet
import Flat.unsafe._

object hubs {

  private val closed = IOs.fail("Hub closed!")

  object Hubs {
    def init[T](capacity: Int)(implicit f: Flat[T]): Hub[T] < IOs =
      Channels.init[T](capacity).map { ch =>
        IOs {
          val listeners = new CopyOnWriteArraySet[Channel[T]]
          Fibers.init {
            def loop(): Unit < Fibers =
              ch.take.map { v =>
                IOs {
                  val l =
                    listeners.toArray
                      .toList.asInstanceOf[List[Channel[T]]]
                      .map(child => Tries.run(child.put(v)))
                  Fibers.parallel(l).map(_ => loop())
                }
              }
            loop()
          }.map { fiber =>
            new Hub(ch, fiber, listeners)(f)
          }
        }
      }
  }

  class Hub[T] private[hubs] (
      ch: Channel[T],
      fiber: Fiber[Unit],
      listeners: CopyOnWriteArraySet[Channel[T]]
  )(implicit f: Flat[T]) {

    def size: Int < IOs = ch.size

    def offer(v: T): Boolean < IOs = ch.offer(v)

    def offerUnit(v: T): Unit < IOs = ch.offerUnit(v)

    def isEmpty: Boolean < IOs = ch.isEmpty

    def isFull: Boolean < IOs = ch.isFull

    def putFiber(v: T): Fiber[Unit] < IOs = ch.putFiber(v)

    def put(v: T): Unit < Fibers = ch.put(v)

    def isClosed: Boolean < IOs = ch.isClosed

    def close: Option[Seq[T]] < IOs =
      fiber.interrupt.map { _ =>
        ch.close.map { r =>
          val it = listeners.iterator()
          def loop(): Unit < IOs =
            IOs {
              if (it.hasNext()) {
                Tries.run(it.next().close)
                  .map(_ => loop())
              } else {
                ()
              }
            }
          loop().andThen(r)
        }
      }

    def listen: Listener[T] < IOs =
      listen(0)

    def listen(bufferSize: Int): Listener[T] < IOs =
      isClosed.map {
        case true => closed
        case false =>
          Channels.init[T](bufferSize).map { child =>
            IOs {
              listeners.add(child)
              isClosed.map {
                case true =>
                  // race condition
                  IOs {
                    listeners.remove(child)
                    closed
                  }
                case false =>
                  new Listener[T](this, child)
              }
            }
          }
      }

    private[hubs] def remove(child: Channel[T]): Unit < IOs =
      IOs {
        listeners.remove(child)
        ()
      }
  }

  class Listener[T] private[hubs] (hub: Hub[T], child: Channel[T]) {

    def size: Int < IOs = child.size

    def isEmpty: Boolean < IOs = child.isEmpty

    def isFull: Boolean < IOs = child.isFull

    def poll: Option[T] < IOs = child.poll

    def takeFiber: Fiber[T] < IOs = child.takeFiber

    def take: T < Fibers = child.take

    def isClosed: Boolean < IOs = child.isClosed

    def close: Option[Seq[T]] < IOs =
      hub.remove(child).andThen(child.close)
  }
}
