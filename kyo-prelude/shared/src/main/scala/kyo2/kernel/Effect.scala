package kyo2.kernel

import internal.*
import kyo.Tag
import scala.util.control.NonFatal

abstract class Effect[-I[_], +O[_]]

object Effect:

    def defer[A, S](f: Safepoint ?=> A < S): A < S =
        new KyoDefer[A, S]:
            def frame = summon[Frame]
            def apply(dummy: Unit, context: Context)(using Safepoint) =
                f

    case class SuspendOps[A](dummy: Unit) extends AnyVal:

        inline def apply[I[_], O[_], E <: Effect[I, O]](
            inline _tag: Tag[E],
            inline _input: I[A]
        )(using inline _frame: Frame): O[A] < E =
            new KyoSuspend[I, O, E, A, O[A], E]:
                def frame = _frame
                def tag   = _tag
                def input = _input
                def apply(v: O[A], context: Context)(using Safepoint) =
                    v
    end SuspendOps

    inline def suspend[A]: SuspendOps[A] = SuspendOps(())

    class SuspendMapOps[A](dummy: Unit) extends AnyVal:
        inline def apply[I[_], O[_], E <: Effect[I, O], B, S](
            inline _tag: Tag[E],
            inline _input: I[A]
        )(
            inline _cont: Safepoint ?=> O[A] => B < S
        )(using inline _frame: Frame): B < (S & E) =
            new KyoSuspend[I, O, E, A, B, S & E]:
                def frame = _frame
                def tag   = _tag
                def input = _input
                def apply(v: O[A], context: Context)(using Safepoint) =
                    Safepoint.handle(
                        suspend = _cont(v),
                        continue = _cont(v)
                    )
    end SuspendMapOps

    inline def suspendMap[A]: SuspendMapOps[A] = SuspendMapOps(())

    object handle:
        inline def apply[I[_], O[_], E <: Effect[I, O], A, B, S, S2, S3](
            inline tag: Tag[E],
            v: A < (E & S)
        )(
            inline handle: Safepoint ?=> [C] => (I[C], O[C] => A < (E & S & S2)) => A < (E & S & S2),
            inline done: A => B < S3 = (v: A) => v,
            accept: [C] => (I[C], Tag[Any]) => Boolean = [C] => (v: I[C], tag2: Tag[Any]) => tag =:= tag2
        )(using inline _frame: Frame, safepoint: Safepoint): B < (S & S2 & S3) =
            def handleLoop(v: A < (E & S & S2 & S3), context: Context)(using Safepoint): B < (S & S2 & S3) =
                v match
                    case <(kyo: KyoSuspend[I, O, E, Any, A, E & S & S2] @unchecked) if accept(kyo.input, kyo.tag.erased) =>
                        Safepoint.handle(
                            suspend = handleLoop(kyo, context),
                            continue = handleLoop(handle[Any](kyo.input, kyo(_, context)), context)
                        )
                    case <(kyo: KyoSuspend[IX, OX, EX, Any, A, E & S & S2 & S3] @unchecked) =>
                        new KyoSuspend[IX, OX, EX, Any, B, S & S2 & S3]:
                            val tag   = kyo.tag
                            val input = kyo.input
                            def frame = _frame
                            def apply(v: OX[Any], context: Context)(using Safepoint) =
                                handleLoop(kyo(v, context), context)
                        end new
                    case <(kyo) =>
                        done(kyo.asInstanceOf[A])
                end match
            end handleLoop
            handleLoop(v, Context.empty)
        end apply

        inline def apply[I1[_], O1[_], E1 <: Effect[I1, O1], I2[_], O2[_], E2 <: Effect[I2, O2], A, S, S2](
            inline tag1: Tag[E1],
            inline tag2: Tag[E2],
            v: A < (E1 & E2 & S)
        )(
            inline handle1: Safepoint ?=> [C] => (I1[C], O1[C] => A < (E1 & E2 & S & S2)) => A < (E1 & E2 & S & S2),
            inline handle2: Safepoint ?=> [C] => (I2[C], O2[C] => A < (E1 & E2 & S & S2)) => A < (E1 & E2 & S & S2)
        )(using inline _frame: Frame, safepoint: Safepoint): A < (S & S2) =
            def handle2Loop(kyo: A < (E1 & E2 & S & S2), context: Context)(using Safepoint): A < (S & S2) =
                kyo match
                    case <(kyo: KyoSuspend[I1, O1, E1, Any, A, E1 & E2 & S & S2] @unchecked) if tag1 =:= kyo.tag =>
                        Safepoint.handle(
                            suspend = handle2Loop(kyo, context),
                            continue = handle2Loop(handle1[Any](kyo.input, kyo(_, context)), context)
                        )
                    case <(kyo: KyoSuspend[I2, O2, E2, Any, A, E1 & E2 & S & S2] @unchecked) if tag2 =:= kyo.tag =>
                        Safepoint.handle(
                            suspend = handle2Loop(kyo, context),
                            continue = handle2Loop(handle2[Any](kyo.input, kyo(_, context)), context)
                        )
                    case <(kyo: KyoSuspend[IX, OX, EX, Any, A, E1 & E2 & S & S2] @unchecked) =>
                        new KyoSuspend[IX, OX, EX, Any, A, S & S2]:
                            val tag   = kyo.tag
                            val input = kyo.input
                            def frame = _frame
                            def apply(v: OX[Any], context: Context)(using Safepoint) =
                                handle2Loop(kyo(v, context), context)
                        end new
                    case <(kyo) =>
                        kyo.asInstanceOf[A]
                end match
            end handle2Loop
            handle2Loop(v, Context.empty)
        end apply

        inline def apply[I1[_], O1[_], E1 <: Effect[I1, O1], I2[_], O2[_], E2 <: Effect[I2, O2], I3[_], O3[_], E3 <: Effect[
            I3,
            O3
        ], A, S, S2](
            inline tag1: Tag[E1],
            inline tag2: Tag[E2],
            inline tag3: Tag[E3],
            v: A < (E1 & E2 & E3 & S)
        )(
            inline handle1: Safepoint ?=> [C] => (I1[C], O1[C] => A < (E1 & E2 & E3 & S & S2)) => A < (E1 & E2 & E3 & S & S2),
            inline handle2: Safepoint ?=> [C] => (I2[C], O2[C] => A < (E1 & E2 & E3 & S & S2)) => A < (E1 & E2 & E3 & S & S2),
            inline handle3: Safepoint ?=> [C] => (I3[C], O3[C] => A < (E1 & E2 & E3 & S & S2)) => A < (E1 & E2 & E3 & S & S2)
        )(using inline _frame: Frame, safepoint: Safepoint): A < (S & S2) =
            def handle3Loop(v: A < (E1 & E2 & E3 & S & S2), context: Context)(using Safepoint): A < (S & S2) =
                v match
                    case <(kyo: KyoSuspend[I1, O1, E1, Any, A, E1 & E2 & E3 & S & S2] @unchecked) if tag1 =:= kyo.tag =>
                        Safepoint.handle(
                            suspend = handle3Loop(kyo, context),
                            continue = handle3Loop(handle1[Any](kyo.input, kyo(_, context)), context)
                        )
                    case <(kyo: KyoSuspend[I2, O2, E2, Any, A, E1 & E2 & E3 & S & S2] @unchecked) if tag2 =:= kyo.tag =>
                        Safepoint.handle(
                            suspend = handle3Loop(kyo, context),
                            continue = handle3Loop(handle2[Any](kyo.input, kyo(_, context)), context)
                        )
                    case <(kyo: KyoSuspend[I3, O3, E3, Any, A, E1 & E2 & E3 & S & S2] @unchecked) if tag3 =:= kyo.tag =>
                        Safepoint.handle(
                            suspend = handle3Loop(kyo, context),
                            continue = handle3Loop(handle3[Any](kyo.input, kyo(_, context)), context)
                        )
                    case <(kyo: KyoSuspend[IX, OX, EX, Any, A, E1 & E2 & E3 & S & S2] @unchecked) =>
                        new KyoSuspend[IX, OX, EX, Any, A, S & S2]:
                            val tag   = kyo.tag
                            val input = kyo.input
                            def frame = _frame
                            def apply(v: OX[Any], context: Context)(using Safepoint) =
                                handle3Loop(kyo(v, context), context)
                        end new
                    case <(kyo) =>
                        kyo.asInstanceOf[A]
                end match
            end handle3Loop
            handle3Loop(v, Context.empty)
        end apply

        inline def state[I[_], O[_], E <: Effect[I, O], State, A, U, S, S2](
            inline tag: Tag[E],
            inline state: State,
            v: A < (E & S)
        )(
            inline handle: Safepoint ?=> [C] => (I[C], State, O[C] => A < (E & S & S2)) => (State, A < (E & S & S2)),
            inline done: (State, A) => U < (S & S2) = (_: State, v: A) => v,
            accept: [C] => (I[C], Tag[Any]) => Boolean = [C] => (v: I[C], tag2: Tag[Any]) => tag =:= tag2
        )(using inline _frame: Frame, safepoint: Safepoint): U < (S & S2) =
            def handleLoop(state: State, v: A < (E & S & S2), context: Context)(using Safepoint): U < (S & S2) =
                v match
                    case <(kyo: KyoSuspend[I, O, E, Any, A, E & S & S2] @unchecked) if accept(kyo.input, kyo.tag.erased) =>
                        Safepoint.handle(
                            suspend = handleLoop(state, kyo, context),
                            continue =
                                val (nst, res) = handle(kyo.input, state, kyo(_, context))
                                handleLoop(nst, res, context)
                        )
                    case <(kyo: KyoSuspend[IX, OX, EX, Any, A, E & S & S2] @unchecked) =>
                        new KyoSuspend[IX, OX, EX, Any, U, S & S2]:
                            val tag   = kyo.tag
                            val input = kyo.input
                            def frame = _frame
                            def apply(v: OX[Any], context: Context)(using Safepoint) =
                                handleLoop(state, kyo(v, context), context)
                        end new
                    case <(kyo) =>
                        done(state, kyo.asInstanceOf[A])
                end match
            end handleLoop
            handleLoop(state, v, Context.empty)
        end state

    end handle

    inline def catching[A, S, B >: A, S2](v: => A < S)(
        inline pf: PartialFunction[Throwable, B < S2]
    )(using inline _frame: Frame, safepoint: Safepoint): B < (S & S2) =
        def catchingLoop(v: B < (S & S2))(using Safepoint): B < (S & S2) =
            (v: @unchecked) match
                case <(kyo: KyoSuspend[IX, OX, EX, Any, B, S & S2] @unchecked) =>
                    new KyoSuspend[IX, OX, EX, Any, B, S & S2]:
                        val tag   = kyo.tag
                        val input = kyo.input
                        def frame = _frame
                        def apply(v: OX[Any], context: Context)(using Safepoint) =
                            try catchingLoop(kyo(v, context))
                            catch
                                case ex: Throwable if (NonFatal(ex) && pf.isDefinedAt(ex)) =>
                                    pf(ex)
                            end try
                        end apply
                case _ =>
                    v
        try catchingLoop(v)
        catch
            case ex: Throwable if (NonFatal(ex) && pf.isDefinedAt(ex)) =>
                pf(ex)
        end try
    end catching
end Effect
