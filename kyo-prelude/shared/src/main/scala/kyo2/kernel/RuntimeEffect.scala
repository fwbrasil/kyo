package kyo2.kernel

import internal.*
import kyo.Tag
import kyo2.bug
import kyo2.isNull
import scala.util.NotGiven

abstract class RuntimeEffect[+A]

object RuntimeEffect:

    inline def suspend[A, E <: RuntimeEffect[A]](inline tag: Tag[E]): A < E =
        suspend(tag)(v => v)

    inline def suspend[A, E <: RuntimeEffect[A], B, S](
        inline tag: Tag[E]
    )(
        inline f: Safepoint ?=> A => B < S
    ): B < (E & S) =
        suspend(tag, bug("Unexpected pending runtime effect: " + tag.show))(f)

    inline def suspend[A, E <: RuntimeEffect[A]](
        inline tag: Tag[E],
        inline default: => A
    ): A < Any =
        suspend(tag, default)(v => v)

    inline def suspend[A, E <: RuntimeEffect[A], B, S](
        inline _tag: Tag[E],
        inline default: => A
    )(
        inline f: Safepoint ?=> A => B < S
    )(using inline _frame: Frame): B < S =
        new KyoDefer[B, S]:
            def frame = _frame
            def apply(v: Unit, values: Values)(using Safepoint) =
                Safepoint.handle(
                    suspend = this,
                    continue = f(values.getOrElse(_tag, default).asInstanceOf[A])
                )

    inline def handle[A, E <: RuntimeEffect[A], B, S](
        inline _tag: Tag[E],
        inline ifUndefined: A,
        inline ifDefined: A => A
    )(v: B < (E & S))(
        using inline _frame: Frame
    ): B < S =
        def handleLoop(v: B < (E & S))(using Safepoint): B < S =
            v match
                case <(kyo: KyoSuspend[IX, OX, EX, Any, B, S] @unchecked) =>
                    new KyoSuspend[IX, OX, EX, Any, B, S]:
                        val tag   = kyo.tag
                        val input = kyo.input
                        def frame = _frame
                        def apply(v: OX[Any], values: Values)(using Safepoint) =
                            val tag   = _tag
                            val value = values.getOrElse(tag, null)
                            val updated =
                                if isNull(value) then values.set(tag, ifUndefined)
                                else values.set(tag, ifDefined(value.asInstanceOf[A]))
                            handleLoop(kyo(v, updated))
                        end apply
                case <(kyo) =>
                    kyo.asInstanceOf[B]
        handleLoop(v)
    end handle

    type WithoutRuntimeEffects[S] = S match
        case RuntimeEffect[x] => Any
        case s1 & s2          => WithoutRuntimeEffects[s1] & WithoutRuntimeEffects[s2]
        case _                => S

    def boundary[A, B, S, S2](v: A < S)(
        f: A < WithoutRuntimeEffects[S] => B < S2
    )(using _frame: Frame): B < (S & S2) =
        new KyoDefer[B, S & S2]:
            def frame = _frame
            def apply(dummy: Unit, values: Values)(using safepoint: Safepoint) =
                val state = safepoint.save(values)
                def boundaryLoop(v: A < S): A < S =
                    v match
                        case <(kyo: KyoSuspend[IX, OX, EX, Any, A, S] @unchecked) =>
                            new KyoSuspend[IX, OX, EX, Any, A, S]:
                                val tag   = kyo.tag
                                val input = kyo.input
                                def frame = _frame
                                def apply(v: OX[Any], values: Values)(using Safepoint) =
                                    val parent = Safepoint.local.get()
                                    Safepoint.local.set(Safepoint(parent.depth, state))
                                    val r =
                                        try kyo(v, state.values)
                                        finally Safepoint.local.set(parent)
                                    boundaryLoop(r)
                                end apply
                        case _ =>
                            v
                f(Effect.defer(boundaryLoop(v).asInstanceOf[A < WithoutRuntimeEffects[S]]))
            end apply
        end new
    end boundary
end RuntimeEffect
