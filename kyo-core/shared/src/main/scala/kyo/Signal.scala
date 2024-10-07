package kyo

import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CopyOnWriteArraySet
import scala.annotation.tailrec

sealed class Signal[A] private ():
    self =>

    private val receivers = new CopyOnWriteArraySet[WeakReference[A => Unit < Async]]

    def send(value: A)(using Frame): Unit < Async =
        IO {
            if receivers.isEmpty() then ()
            else
                val it      = receivers.iterator()
                val builder = Seq.newBuilder[Unit < Async]
                builder.sizeHint(receivers.size())
                @tailrec def loop(): Unit =
                    if it.hasNext() then
                        it.next().get() match
                            case null     => it.remove()
                            case receiver => builder.addOne(IO(receiver(value)))
                        loop()
                loop()
                Async.parallel(builder.result()).unit
        }

    def receive(f: A => Unit < Async)(using Frame): Unit < (IO & Resource) =
        IO {
            val ref = new WeakReference(f)
            receivers.add(ref)
            Resource.ensure {
                discard(f) // keep strong reference
                discard(receivers.remove(ref))
            }.andThen {
                IO(discard(receivers.add(ref)))
            }
        }

    def receiveWeak(f: A => Unit < Async)(using Frame): Unit < IO =
        IO(addReceiver(f))

    private inline def addReceiver[B](inline f: A => Unit < Async)(using inline frame: Frame): Unit =
        discard(receivers.add(new WeakReference(f)))

    def map[B, S](f: A => B < Async)(using Frame): Signal[B] < Async =
        IO {
            val b = new Signal[B] with (A => Unit < Async):
                def apply(value: A) = f(value).map(send)
            addReceiver(b)
            b
        }

    def flatMap[B, S](f: A => Signal[B] < Async)(using Frame): Signal[B] < Async =
        IO {
            val b = new Signal[B] with (A => Unit < Async):
                def apply(value: A) = f(value).map(_.receiveWeak(send))
            addReceiver(b)
            b
        }

    def filter(f: A => Boolean < Async)(using Frame): Signal[A] < Async =
        IO {
            val filtered = new Signal[A] with (A => Unit < Async):
                def apply(value: A) =
                    f(value).map {
                        case true  => send(value)
                        case false =>
                    }
            addReceiver(filtered)
            filtered
        }

end Signal

object Signal:

    def init[A](using Frame): Signal[A] < IO = IO(new Signal[A])
