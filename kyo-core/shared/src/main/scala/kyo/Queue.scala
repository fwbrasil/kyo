package kyo

import java.util.concurrent.atomic.AtomicReference
import org.jctools.queues.*
import scala.annotation.tailrec

/** A thread-safe queue implementation based on JCTools.
  *
  * This queue provides various concurrency-safe operations and supports different access patterns (MPMC, MPSC, SPMC, SPSC) using JCTools'
  * efficient non-blocking data structures under the hood.
  *
  * @tparam A
  *   the type of elements in the queue
  */
opaque type Queue[A] = Queue.Unsafe[A]

object Queue:

    extension [A](self: Queue[A])
        /** Returns the capacity of the queue.
          *
          * @return
          *   the capacity of the queue
          */
        def capacity: Int = self.capacity

        /** Returns the current size of the queue.
          *
          * @return
          *   the current size of the queue
          */
        def size(using Frame): Int < (IO & Abort[Closed]) = IO.Unsafe(Abort.get(self.size()))

        /** Checks if the queue is empty.
          *
          * @return
          *   true if the queue is empty, false otherwise
          */
        def empty(using Frame): Boolean < (IO & Abort[Closed]) = IO.Unsafe(Abort.get(self.empty()))

        /** Checks if the queue is full.
          *
          * @return
          *   true if the queue is full, false otherwise
          */
        def full(using Frame): Boolean < (IO & Abort[Closed]) = IO.Unsafe(Abort.get(self.full()))

        /** Offers an element to the queue.
          *
          * @param v
          *   the element to offer
          * @return
          *   true if the element was added, false if the queue is full or closed
          */
        def offer(v: A)(using Frame): Boolean < (IO & Abort[Closed]) = IO.Unsafe(Abort.get(self.offer(v)))

        /** Offers an element to the queue and discards the result
          *
          * @param v
          *   the element to offer
          */
        def offerDiscard(v: A)(using Frame): Unit < (IO & Abort[Closed]) = IO.Unsafe(Abort.get(self.offer(v).unit))

        /** Polls an element from the queue.
          *
          * @return
          *   Maybe containing the polled element, or empty if the queue is empty
          */
        def poll(using Frame): Maybe[A] < (IO & Abort[Closed]) = IO.Unsafe(Abort.get(self.poll()))

        /** Peeks at the first element in the queue without removing it.
          *
          * @return
          *   Maybe containing the first element, or empty if the queue is empty
          */
        def peek(using Frame): Maybe[A] < (IO & Abort[Closed]) = IO.Unsafe(Abort.get(self.peek()))

        /** Drains all elements from the queue.
          *
          * @return
          *   a sequence of all elements in the queue
          */
        def drain(using Frame): Seq[A] < (IO & Abort[Closed]) = IO.Unsafe(Abort.get(self.drain()))

        /** Closes the queue and returns any remaining elements.
          *
          * @return
          *   a sequence of remaining elements
          */
        def close(using Frame): Maybe[Seq[A]] < IO = IO.Unsafe(self.close())

        /** Checks if the queue is closed.
          *
          * @return
          *   true if the queue is closed, false otherwise
          */
        def closed(using Frame): Boolean < IO = IO.Unsafe(self.closed())

        /** Returns the unsafe version of the queue.
          *
          * @return
          *   the unsafe version of the queue
          */
        def unsafe: Unsafe[A] = self
    end extension

    /** Initializes a new queue with the specified capacity and access pattern. The actual capacity will be rounded up to the next power of
      * two.
      *
      * @param capacity
      *   the desired capacity of the queue. Note that this will be rounded up to the next power of two.
      * @param access
      *   the access pattern (default is MPMC)
      * @return
      *   a new Queue instance with a capacity that is the next power of two greater than or equal to the specified capacity
      *
      * @note
      *   The actual capacity will be rounded up to the next power of two.
      * @warning
      *   The actual capacity may be larger than the specified capacity due to rounding.
      */
    def init[A](capacity: Int, access: Access = Access.MultiProducerMultiConsumer)(using Frame): Queue[A] < IO =
        IO.Unsafe(Unsafe.init(capacity, access))

    /** An unbounded queue that can grow indefinitely.
      *
      * @tparam A
      *   the type of elements in the queue
      */
    opaque type Unbounded[A] <: Queue[A] = Queue[A]

    object Unbounded:
        extension [A](self: Unbounded[A])
            /** Adds an element to the unbounded queue.
              *
              * @param value
              *   the element to add
              */
            def add(value: A)(using Frame): Unit < IO = IO.Unsafe(Unsafe.add(self)(value))

            def unsafe: Unsafe[A] = self
        end extension

        /** Initializes a new unbounded queue with the specified access pattern and chunk size.
          *
          * @param access
          *   the access pattern (default is MPMC)
          * @param chunkSize
          *   the chunk size for internal array allocation (default is 8)
          * @return
          *   a new Unbounded Queue instance
          */
        def init[A](access: Access = Access.MultiProducerMultiConsumer, chunkSize: Int = 8)(using Frame): Unbounded[A] < IO =
            IO.Unsafe(Unsafe.init(access, chunkSize))

        /** Initializes a new dropping queue with the specified capacity and access pattern.
          *
          * @param capacity
          *   the capacity of the queue. Note that this will be rounded up to the next power of two.
          * @param access
          *   the access pattern (default is MPMC)
          * @return
          *   a new Unbounded Queue instance that drops elements when full
          *
          * @note
          *   The actual capacity will be rounded up to the next power of two.
          * @warning
          *   The actual capacity may be larger than the specified capacity due to rounding.
          */
        def initDropping[A](capacity: Int, access: Access = Access.MultiProducerMultiConsumer)(using Frame): Unbounded[A] < IO =
            IO.Unsafe(Unsafe.initDropping(capacity, access))

        /** Initializes a new sliding queue with the specified capacity and access pattern.
          *
          * @param capacity
          *   the capacity of the queue. Note that this will be rounded up to the next power of two.
          * @param access
          *   the access pattern (default is MPMC)
          * @return
          *   a new Unbounded Queue instance that slides elements when full
          *
          * @note
          *   The actual capacity will be rounded up to the next power of two.
          * @warning
          *   The actual capacity may be larger than the specified capacity due to rounding.
          */
        def initSliding[A](capacity: Int, access: Access = Access.MultiProducerMultiConsumer)(using Frame): Unbounded[A] < IO =
            IO.Unsafe(Unsafe.initSliding(capacity, access))

        /** WARNING: Low-level API meant for integrations, libraries, and performance-sensitive code. See AllowUnsafe for more details. */
        opaque type Unsafe[A] <: Queue.Unsafe[A] = Queue[A]

        /** WARNING: Low-level API meant for integrations, libraries, and performance-sensitive code. See AllowUnsafe for more details. */
        object Unsafe:
            extension [A](self: Unsafe[A])
                def add(value: A)(using AllowUnsafe, Frame): Unit = discard(self.offer(value))
                def safe: Unbounded[A]                            = self

            def init[A](access: Access = Access.MultiProducerMultiConsumer, chunkSize: Int = 8)(
                using
                Frame,
                AllowUnsafe
            ): Unsafe[A] =
                access match
                    case Access.MultiProducerMultiConsumer =>
                        Queue.Unsafe.fromJava(new MpmcUnboundedXaddArrayQueue[A](chunkSize))
                    case Access.MultiProducerSingleConsumer =>
                        Queue.Unsafe.fromJava(new MpscUnboundedArrayQueue[A](chunkSize))
                    case Access.SingleProducerMultiConsumer =>
                        Queue.Unsafe.fromJava(new MpmcUnboundedXaddArrayQueue[A](chunkSize))
                    case Access.SingleProducerSingleConsumer =>
                        Queue.Unsafe.fromJava(new SpscUnboundedArrayQueue[A](chunkSize))

            def initDropping[A](_capacity: Int, access: Access = Access.MultiProducerMultiConsumer)(
                using
                frame: Frame,
                allow: AllowUnsafe
            ): Unsafe[A] =
                new Unsafe[A]:
                    val underlying                           = Queue.Unsafe.init[A](_capacity, access)
                    def capacity                             = _capacity
                    def size()(using AllowUnsafe)            = underlying.size()
                    def empty()(using AllowUnsafe)           = underlying.empty()
                    def full()(using AllowUnsafe)            = underlying.full().map(_ => false)
                    def offer(v: A)(using AllowUnsafe)       = underlying.offer(v).map(_ => true)
                    def poll()(using AllowUnsafe)            = underlying.poll()
                    def peek()(using AllowUnsafe)            = underlying.peek()
                    def drain()(using AllowUnsafe)           = underlying.drain()
                    def close()(using Frame, AllowUnsafe)    = underlying.close()
                    def closed()(using AllowUnsafe): Boolean = underlying.closed()
                end new
            end initDropping

            def initSliding[A](_capacity: Int, access: Access = Access.MultiProducerMultiConsumer)(
                using
                frame: Frame,
                allow: AllowUnsafe
            ): Unsafe[A] =
                new Unsafe[A]:
                    val underlying                 = Queue.Unsafe.init[A](_capacity, access)
                    def capacity                   = _capacity
                    def size()(using AllowUnsafe)  = underlying.size()
                    def empty()(using AllowUnsafe) = underlying.empty()
                    def full()(using AllowUnsafe)  = underlying.full().map(_ => false)
                    def offer(v: A)(using AllowUnsafe) =
                        @tailrec def loop(v: A): Result[Closed, Boolean] =
                            underlying.offer(v) match
                                case Result.Success(false) =>
                                    discard(underlying.poll())
                                    loop(v)
                                case result =>
                                    result
                        end loop
                        loop(v)
                    end offer
                    def poll()(using AllowUnsafe)            = underlying.poll()
                    def peek()(using AllowUnsafe)            = underlying.peek()
                    def drain()(using AllowUnsafe)           = underlying.drain()
                    def close()(using Frame, AllowUnsafe)    = underlying.close()
                    def closed()(using AllowUnsafe): Boolean = underlying.closed()
                end new
            end initSliding
        end Unsafe
    end Unbounded

    /** WARNING: Low-level API meant for integrations, libraries, and performance-sensitive code. See AllowUnsafe for more details. */
    abstract class Unsafe[A]:
        def capacity: Int
        def size()(using AllowUnsafe): Result[Closed, Int]
        def empty()(using AllowUnsafe): Result[Closed, Boolean]
        def full()(using AllowUnsafe): Result[Closed, Boolean]
        def offer(v: A)(using AllowUnsafe): Result[Closed, Boolean]
        def poll()(using AllowUnsafe): Result[Closed, Maybe[A]]
        def peek()(using AllowUnsafe): Result[Closed, Maybe[A]]
        def drain()(using AllowUnsafe): Result[Closed, Seq[A]]
        def close()(using Frame, AllowUnsafe): Maybe[Seq[A]]
        def closed()(using AllowUnsafe): Boolean
        final def safe: Queue[A] = this
    end Unsafe

    /** WARNING: Low-level API meant for integrations, libraries, and performance-sensitive code. See AllowUnsafe for more details. */
    object Unsafe:

        abstract private class Base[A](initFrame: Frame) extends Unsafe[A]:
            import AllowUnsafe.embrace.danger
            final private val state                 = AtomicInt.Unsafe.init(0)
            @volatile final private var closedError = Maybe.empty[Result.Error[Closed]]

            final def close()(using frame: Frame, allow: AllowUnsafe) =
                @tailrec def loop(): Maybe[Seq[A]] =
                    val s = state.get()
                    if s == -1 then
                        Maybe.empty
                    else if !state.cas(s, -1) then
                        loop()
                    else
                        closedError = Maybe(Result.Fail(Closed("Queue", initFrame, frame)))
                        Maybe(drain())
                    end if
                end loop
                loop()
            end close

            final def closed()(using AllowUnsafe) = state.get() == -1

            final def drain()(using AllowUnsafe): Result[Closed, Seq[A]] =
                @tailrec def loop(acc: Chunk[A]): Result[Closed, Seq[A]] =
                    this.poll() match
                        case Result.Success(maybe) =>
                            maybe match
                                case Absent     => Result.success(acc)
                                case Present(v) => loop(acc.append(v))
                        case result: Result.Error[Closed] @unchecked =>
                            result
                end loop
                loop(Chunk.empty)
            end drain

            protected /*inline*/ def op[A]( /*inline*/ f: => A): Result[Closed, A] =
                @tailrec def loop(): Result[Closed, A] =
                    val s = state.get()
                    if s == -1 then
                        closedError.getOrElse(loop())
                    else
                        Result(f)
                    end if
                end loop
                loop()
            end op

            protected /*inline*/ def offerOp[A]( /*inline*/ f: => Boolean, /*inline*/ raceRepair: => Boolean): Result[Closed, Boolean] =
                @tailrec def loop(): Result[Closed, Boolean] =
                    val s = state.get()
                    if s == -1 then
                        closedError.getOrElse(loop())
                    else if s == this.capacity then
                        Result.success(false)
                    else if !state.cas(s, s + 1) then
                        loop()
                    else
                        val result = f
                        require(result)
                        if state.get() == -1 then
                            Result(raceRepair)
                        else
                            Result.success(result)
                        end if
                    end if
                end loop
                loop()
            end offerOp

            protected inline def pollOp(inline f: => Maybe[A]): Result[Closed, Maybe[A]] =
                @tailrec def loop(): Result[Closed, Maybe[A]] =
                    val s = state.get()
                    if s == -1 then
                        closedError.getOrElse(loop())
                    else if s == 0 then
                        Result.success(Maybe.empty)
                    else if !state.cas(s, s - 1) then
                        loop()
                    else
                        Result {
                            def resultLoop(): Maybe[A] =
                                val r = f
                                if r.isEmpty then resultLoop()
                                else r
                            end resultLoop
                            resultLoop()
                        }
                    end if
                end loop
                loop()
            end pollOp
        end Base

        def init[A](capacity: Int, access: Access = Access.MultiProducerMultiConsumer)(using
            initFrame: Frame,
            allow: AllowUnsafe
        ): Unsafe[A] =
            capacity match
                case _ if capacity <= 0 =>
                    new Base[A](initFrame):
                        def capacity                       = 0
                        def size()(using AllowUnsafe)      = op(0)
                        def empty()(using AllowUnsafe)     = op(true)
                        def full()(using AllowUnsafe)      = op(true)
                        def offer(v: A)(using AllowUnsafe) = offerOp(false, false)
                        def poll()(using AllowUnsafe)      = pollOp(Maybe.empty)
                        def peek()(using AllowUnsafe)      = op(Maybe.empty)
                case 1 =>
                    new Base[A](initFrame):
                        private val state                  = AtomicRef.Unsafe.init(Maybe.empty[A])
                        def capacity                       = 1
                        def empty()(using AllowUnsafe)     = op(state.get().isEmpty)
                        def size()(using AllowUnsafe)      = op(if state.get().isEmpty then 0 else 1)
                        def full()(using AllowUnsafe)      = op(state.get().isDefined)
                        def offer(v: A)(using AllowUnsafe) = offerOp(state.cas(Maybe.empty, Maybe(v)), !state.cas(Maybe(v), Maybe.empty))
                        def poll()(using AllowUnsafe)      = pollOp(state.getAndSet(Maybe.empty))
                        def peek()(using AllowUnsafe)      = op(state.get())
                case Int.MaxValue =>
                    Unbounded.Unsafe.init(access).safe
                case _ =>
                    access match
                        case Access.MultiProducerMultiConsumer =>
                            fromJava(new MpmcArrayQueue[A](capacity), capacity)
                        case Access.MultiProducerSingleConsumer =>
                            fromJava(new MpscArrayQueue[A](capacity), capacity)
                        case Access.SingleProducerMultiConsumer =>
                            fromJava(new SpmcArrayQueue[A](capacity), capacity)
                        case Access.SingleProducerSingleConsumer =>
                            if capacity >= 4 then
                                fromJava(new SpscArrayQueue[A](capacity), capacity)
                            else
                                // Spsc queue doesn't support capacity < 4
                                fromJava(new SpmcArrayQueue[A](capacity), capacity)

        def fromJava[A](q: java.util.Queue[A], _capacity: Int = Int.MaxValue)(using initFrame: Frame, allow: AllowUnsafe): Unsafe[A] =
            new Base[A](initFrame):
                def capacity                   = _capacity
                def size()(using AllowUnsafe)  = op(q.size())
                def empty()(using AllowUnsafe) = op(q.isEmpty())
                def full()(using AllowUnsafe)  = op(q.size() >= _capacity)
                def offer(v: A)(using AllowUnsafe) =
                    offerOp(
                        q.offer(v),
                        try !q.remove(v)
                        catch
                            case _: UnsupportedOperationException =>
                                // TODO the race repair should use '!q.remove(v)' but JCTools doesn't support the operation.
                                // In rare cases, items may be left in the queue permanently after closing due to this limitation.
                                // The item will only be removed when the queue object itself is garbage collected.
                                !q.contains(v)
                    )
                def poll()(using AllowUnsafe) = pollOp(Maybe(q.poll()))
                def peek()(using AllowUnsafe) = op(Maybe(q.peek()))

    end Unsafe

end Queue
