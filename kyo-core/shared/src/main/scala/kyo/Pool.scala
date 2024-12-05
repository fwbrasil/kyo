package kyo

/** A thread-safe pool for reusing objects.
  *
  * Pools allow efficient reuse of expensive-to-create objects by maintaining a fixed number of instances that can be temporarily borrowed
  * and automatically returned. When objects are requested, they are taken from the pool if available, or created if the pool is empty.
  * Objects are automatically returned to the pool after use.
  *
  * @tparam A
  *   The type of objects in the pool
  * @tparam S
  *   The effect type required to create pool objects
  */
abstract class Pool[A, S]:

    /** Uses an object from the pool.
      *
      * Gets an available object from the pool, or creates a new one if none are available. The object is automatically returned to the pool
      * after the provided function completes, whether it succeeds or fails.
      *
      * @param f
      *   The function to apply to the pooled object
      * @return
      *   The result of applying the function to a pooled object
      */
    def use[B, S2](f: A => B < S2): B < (S & S2 & IO & Abort[Closed])

    /** Returns the maximum capacity of the pool.
      *
      * @return
      *   The maximum number of objects the pool can hold
      */
    def capacity: Int

    /** Returns the current number of objects in the pool.
      *
      * @return
      *   The current number of available objects in the pool
      */
    def size: Int < (IO & Abort[Closed])

    /** Closes the pool and retrieves any remaining objects.
      *
      * @return
      *   A Maybe containing any objects that were in the pool at the time of closing, or empty if the pool was already closed
      */
    def close: Maybe[Seq[A]] < IO
end Pool

object Pool:

    /** Initializes a new pool with the specified capacity.
      *
      * Creates a new pool that will create objects using the provided creation function when needed. The pool will maintain at most
      * `capacity` objects at any time.
      *
      * @param capacity
      *   The maximum number of objects the pool should maintain
      * @param create
      *   The function to create new objects when needed
      * @return
      *   A new Pool instance
      */
    def init[A, S](capacity: Int)(create: => A < S)(using Frame): Pool[A, S] < IO =
        Queue.init[A](capacity).map { queue =>
            new Pool[A, S]:
                def use[B, S2](f: A => B < S2) =
                    queue.poll.map {
                        case Absent         => create
                        case Present(value) => value
                    }.map { value =>
                        IO.ensure(Abort.run(queue.offer(value)).unit) {
                            f(value)
                        }
                    }
                def capacity = queue.capacity
                def size     = queue.size
                def close    = queue.close
        }
    end init
end Pool
