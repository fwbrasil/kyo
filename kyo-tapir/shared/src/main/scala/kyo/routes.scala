package kyo

import kyo.internal.KyoSttpMonad
import kyo.internal.KyoSttpMonad.*
import kyo.internal.Trace
import scala.reflect.ClassTag
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.NettyKyoServer
import sttp.tapir.server.netty.NettyKyoServerBinding

case class Route(endpoint: ServerEndpoint[Any, KyoSttpMonad.M]) extends AnyVal

type Routes >: Routes.Effects <: Routes.Effects

object Routes:

    type Effects = Sums[Route] & Fibers

    def run[T, S](v: Unit < (Routes & S))(using Trace): NettyKyoServerBinding < (Fibers & S) =
        run[T, S](NettyKyoServer())(v)

    def run[T, S](server: NettyKyoServer)(v: Unit < (Routes & S))(using Trace): NettyKyoServerBinding < (Fibers & S) =
        Sums.run[Route].apply[Unit, Fibers & S](v).map { (routes, _) =>
            IOs(server.addEndpoints(routes.toSeq.map(_.endpoint).toList).start()): NettyKyoServerBinding < (Fibers & S)
        }
    end run

    def add[A: Tag, I, E: Tag: ClassTag, O: Flat](e: Endpoint[A, I, E, O, Any])(
        f: I => O < (Fibers & Envs[A] & Aborts[E])
    )(using Trace): Unit < Routes =
        Sums.add(
            Route(
                e.serverSecurityLogic[A, KyoSttpMonad.M](a => Right(a)).serverLogic((a: A) =>
                    (i: I) =>
                        Aborts.run(Envs.run(a)(f(i)))
                )
            )
        ).unit

    def add[A: Tag, I, E: Tag: ClassTag, O: Flat](
        e: PublicEndpoint[Unit, Unit, Unit, Any] => Endpoint[A, I, E, O, Any]
    )(
        f: I => O < (Fibers & Envs[A] & Aborts[E])
    )(using Trace): Unit < Routes =
        add(e(endpoint))(f)

    def collect(init: (Unit < Routes)*)(using Trace): Unit < Routes =
        Seqs.collect(init).unit

end Routes
