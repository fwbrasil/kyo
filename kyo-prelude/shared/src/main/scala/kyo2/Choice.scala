package kyo2

import kyo.Tag
import kyo2.kernel.*

sealed trait Choice extends Effect[Seq, Id]

object Choice:

    inline def get[T](seq: Seq[T]): T < Choice =
        Effect.suspend[T](Tag[Choice], seq)

    inline def eval[T, U, S](seq: Seq[T])(inline f: T => U < S): U < (Choice & S) =
        seq match
            case Seq(head) => f(head)
            case seq       => Effect.suspendMap[T](Tag[Choice], seq)(f)

    inline def dropIf(condition: Boolean): Unit < Choice =
        if condition then drop
        else ()

    inline def drop: Nothing < Choice =
        Effect.suspend[Nothing](Tag[Choice], Seq.empty)

    def run[T, S](v: T < (Choice & S))(using Frame): Seq[T] < S =
        Effect.handle(Tag[Choice], v.map(Seq[T](_))) {
            [C] =>
                (input, cont) =>
                    Kyo.seq.map(input)(v => Choice.run(cont(v))).map(_.flatten.flatten)
        }

end Choice
