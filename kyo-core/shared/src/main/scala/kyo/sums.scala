package kyo

import kyo.core.*
import kyo.internal.Trace

class Sums[V] extends Effect[Sums[V]]:
    type Command[T] = V

    private val handler =
        new ResultHandler[Chunk[V], Const[V], Sums[V], [T] =>> (Chunk[V], T), Any]:
            def done[T](st: Chunk[V], v: T)(using Tag[Sums[V]]) = (st, v)
            def resume[T, U: Flat, S](st: Chunk[V], command: V, k: T => U < (Sums[V] & S))(using Tag[Sums[V]]) =
                Resume(st.append(command), k(().asInstanceOf[T]))
end Sums

object Sums:
    private object sums extends Sums[Any]
    private def sums[V]: Sums[V] = sums.asInstanceOf[Sums[V]]

    def add[V](v: V)(using Tag[Sums[V]], Trace): Unit < Sums[V] =
        sums[V].suspend[Unit](v)

    class RunDsl[V]:
        def apply[T: Flat, S](v: T < (Sums[V] & S))(
            using
            Tag[Sums[V]],
            Trace
        ): (Chunk[V], T) < S =
            sums[V].handle(sums[V].handler)(Chunks.init, v)
    end RunDsl

    def run[V >: Nothing]: RunDsl[V] = new RunDsl[V]

end Sums
