package kyo

import core._
import core.internal._
import ios._

object locals {

  abstract class Local[T] {

    import Locals._

    def default: T

    val get: T < IOs =
      new KyoIO[T, Any] {

        def apply(v: Unit < (IOs with Any), s: Safepoint[IO, IOs], l: State) =
          l.getOrElse(Local.this, default).asInstanceOf[T]
      }

    def let[U, S1, S2](v: T < S1)(f: U < S2): U < (S1 with S2 with IOs) = {
      def loop(v: T, f: U < S2): U < S2 =
        f match {
          case kyo: Kyo[MX, EX, Any, U, S2] @unchecked =>
            new KyoCont[MX, EX, Any, U, S2](kyo) {
              def apply(v2: Any < S2, s: Safepoint[MX, EX], l: Locals.State) =
                loop(v, kyo(v2, s, l.updated(Local.this, v)))
            }
          case _ =>
            f
        }
      v.map(loop(_, f))
    }
  }

  object Locals {

    type State = Map[Local[_], Any]

    object State {
      val empty: State = Map.empty
    }

    def init[T](defaultValue: T): Local[T] =
      new Local[T] {
        def default = defaultValue
      }

    val save: State < IOs =
      new KyoIO[State, Any] {
        def apply(v: Unit < (IOs with Any), s: Safepoint[IO, IOs], l: Locals.State) =
          l
      }

    def restore[T, S](st: State)(f: T < S): T < (IOs with S) = {
      def loop(f: T < S): T < S =
        f match {
          case kyo: Kyo[MX, EX, Any, T, S] @unchecked =>
            new KyoCont[MX, EX, Any, T, S](kyo) {
              def apply(v2: Any < S, s: Safepoint[MX, EX], l: Locals.State) =
                loop(kyo(v2, s, l ++ st))
            }
          case _ =>
            f
        }
      loop(f)
    }
  }
}
