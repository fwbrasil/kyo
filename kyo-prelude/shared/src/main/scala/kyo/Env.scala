package kyo

import kyo.*
import kyo.Tag
import kyo.kernel.*
import scala.util.NotGiven

sealed trait Env[+R] extends ContextEffect[TypeMap[R]]

object Env:

    given eliminateEnv: Reducible.Eliminable[Env[Any]] with {}
    private inline def erasedTag[R] = Tag[Env[Any]].asInstanceOf[Tag[Env[R]]]

    inline def get[R](using inline tag: Tag[R])(using inline frame: Frame): R < Env[R] =
        use[R](identity)

    def run[R >: Nothing: Tag, A: Flat, S, VR](env: R)(v: A < (Env[R & VR] & S))(
        using
        reduce: Reducible[Env[VR]],
        frame: Frame
    ): A < (S & reduce.SReduced) =
        runTypeMap(TypeMap(env))(v)

    def runTypeMap[R >: Nothing, A: Flat, S, VR](env: TypeMap[R])(v: A < (Env[R & VR] & S))(
        using
        reduce: Reducible[Env[VR]],
        frame: Frame
    ): A < (S & reduce.SReduced) =
        reduce(ContextEffect.handle(erasedTag[R], env, _.union(env))(v): A < (Env[VR] & S))

    transparent inline def runLayer[A, S, V](inline layers: Layer[?, ?]*)(value: A < (Env[V] & S)): A < Nothing =
        inline Layer.init[V](layers*) match
            case layer: Layer[V, s] =>
                layer.run.map { env =>
                    runTypeMap(env)(value)
                }

    final class UseOps[R >: Nothing](dummy: Unit) extends AnyVal:
        inline def apply[A, S](inline f: R => A < S)(
            using
            inline tag: Tag[R],
            inline frame: Frame
        ): A < (Env[R] & S) =
            ContextEffect.suspendMap(erasedTag[R]) { map =>
                f(map.asInstanceOf[TypeMap[R]].get(using tag))
            }
    end UseOps

    inline def use[R >: Nothing]: UseOps[R] = UseOps(())

end Env
