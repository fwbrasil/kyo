package kyo

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kyo.internal.Trace
import org.jctools.queues.*
import scala.annotation.tailrec

class Queue[T] private[kyo] (private[kyo] val unsafe: Queues.Unsafe[T]):

    def capacity(using Trace): Int               = unsafe.capacity
    def size(using Trace): Int < IOs             = op(unsafe.size())
    def isEmpty(using Trace): Boolean < IOs      = op(unsafe.isEmpty())
    def isFull(using Trace): Boolean < IOs       = op(unsafe.isFull())
    def offer(v: T)(using Trace): Boolean < IOs  = op(unsafe.offer(v))
    def poll(using Trace): Option[T] < IOs       = op(Option(unsafe.poll()))
    def peek(using Trace): Option[T] < IOs       = op(Option(unsafe.peek()))
    def drain(using Trace): Seq[T] < IOs         = op(unsafe.drain())
    def isClosed(using Trace): Boolean < IOs     = IOs(unsafe.isClosed())
    def close(using Trace): Option[Seq[T]] < IOs = IOs(unsafe.close())

    protected inline def op[T, S](inline v: => T < IOs)(using inline trace: Trace): T < IOs =
        IOs {
            if unsafe.isClosed() then
                Queues.closed
            else
                v
        }
end Queue

object Queues:

    private[kyo] val closed = IOs.fail("Queue closed!")

    abstract private[kyo] class Unsafe[T]
        extends AtomicBoolean(false):

        def capacity: Int
        def size(): Int
        def isEmpty(): Boolean
        def isFull(): Boolean
        def offer(v: T): Boolean
        def poll(): T
        def peek(): T

        def drain(): Seq[T] =
            val b = Seq.newBuilder[T]
            @tailrec def loop(): Unit =
                val v = poll()
                if !isNull(v) then
                    b += v
                    loop()
            end loop
            loop()
            b.result()
        end drain

        def isClosed(): Boolean =
            super.get()

        def close(): Option[Seq[T]] =
            super.compareAndSet(false, true) match
                case false =>
                    None
                case true =>
                    Some(drain())
    end Unsafe

    class Unbounded[T] private[kyo] (unsafe: Queues.Unsafe[T]) extends Queue[T](unsafe):
        def add[S](v: T < S)(using Trace): Unit < (IOs & S) =
            op(v.map(offer).unit)

    def init[T](capacity: Int, access: Access = kyo.Access.Mpmc): Queue[T] < IOs =
        IOs {
            capacity match
                case c if (c <= 0) =>
                    new Queue(
                        new Unsafe[T]:
                            def capacity    = 0
                            def size()      = 0
                            def isEmpty()   = true
                            def isFull()    = true
                            def offer(v: T) = false
                            def poll()      = null.asInstanceOf[T]
                            def peek()      = null.asInstanceOf[T]
                    )
                case 1 =>
                    new Queue(
                        new Unsafe[T]:
                            val state       = new AtomicReference[T]
                            def capacity    = 1
                            def size()      = if isNull(state.get()) then 0 else 1
                            def isEmpty()   = isNull(state.get())
                            def isFull()    = !isNull(state.get())
                            def offer(v: T) = state.compareAndSet(null.asInstanceOf[T], v)
                            def poll()      = state.getAndSet(null.asInstanceOf[T])
                            def peek()      = state.get()
                    )
                case Int.MaxValue =>
                    initUnbounded(access)
                case _ =>
                    access match
                        case kyo.Access.Mpmc =>
                            fromJava(new MpmcArrayQueue[T](capacity), capacity)
                        case kyo.Access.Mpsc =>
                            fromJava(new MpscArrayQueue[T](capacity), capacity)
                        case kyo.Access.Spmc =>
                            fromJava(new SpmcArrayQueue[T](capacity), capacity)
                        case kyo.Access.Spsc =>
                            if capacity >= 4 then
                                fromJava(new SpscArrayQueue[T](capacity), capacity)
                            else
                                // Spsc queue doesn't support capacity < 4
                                fromJava(new SpmcArrayQueue[T](capacity), capacity)
        }

    def initUnbounded[T](access: Access = kyo.Access.Mpmc, chunkSize: Int = 8): Unbounded[T] < IOs =
        IOs {
            access match
                case kyo.Access.Mpmc =>
                    fromJava(new MpmcUnboundedXaddArrayQueue[T](chunkSize))
                case kyo.Access.Mpsc =>
                    fromJava(new MpscUnboundedArrayQueue[T](chunkSize))
                case kyo.Access.Spmc =>
                    fromJava(new MpmcUnboundedXaddArrayQueue[T](chunkSize))
                case kyo.Access.Spsc =>
                    fromJava(new SpscUnboundedArrayQueue[T](chunkSize))
        }

    def initDropping[T](capacity: Int, access: Access = kyo.Access.Mpmc): Unbounded[T] < IOs =
        init[T](capacity, access).map { q =>
            val u = q.unsafe
            val c = capacity
            new Unbounded(
                new Unsafe[T]:
                    def capacity  = c
                    def size()    = u.size()
                    def isEmpty() = u.isEmpty()
                    def isFull()  = false
                    def offer(v: T) =
                        u.offer(v)
                        true
                    def poll() = u.poll()
                    def peek() = u.peek()
            )
        }

    def initSliding[T](capacity: Int, access: Access = kyo.Access.Mpmc): Unbounded[T] < IOs =
        init[T](capacity, access).map { q =>
            val u = q.unsafe
            val c = capacity
            new Unbounded(
                new Unsafe[T]:
                    def capacity  = c
                    def size()    = u.size()
                    def isEmpty() = u.isEmpty()
                    def isFull()  = false
                    def offer(v: T) =
                        @tailrec def loop(v: T): Unit =
                            val u = q.unsafe
                            if u.offer(v) then ()
                            else
                                u.poll()
                                loop(v)
                            end if
                        end loop
                        loop(v)
                        true
                    end offer
                    def poll() = u.poll()
                    def peek() = u.peek()
            )
        }

    private def fromJava[T](q: java.util.Queue[T]): Unbounded[T] =
        new Unbounded(
            new Unsafe[T]:
                def capacity    = Int.MaxValue
                def size()      = q.size
                def isEmpty()   = q.isEmpty()
                def isFull()    = false
                def offer(v: T) = q.offer(v)
                def poll()      = q.poll
                def peek()      = q.peek
        )

    private def fromJava[T](q: java.util.Queue[T], _capacity: Int): Queue[T] =
        new Queue(
            new Unsafe[T]:
                def capacity    = _capacity
                def size()      = q.size
                def isEmpty()   = q.isEmpty()
                def isFull()    = q.size >= _capacity
                def offer(v: T) = q.offer(v)
                def poll()      = q.poll
                def peek()      = q.peek
        )
end Queues
