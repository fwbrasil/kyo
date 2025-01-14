package snunit.tapir

import sttp.model._
import sttp.tapir._
import sttp.tapir.server._
import kyo.internal.KyoSttpMonad

class SNUnitKyoServerBuilder private (serverEndpoints: List[ServerEndpoint[Any, KyoSttpMonad.M]]) {

  private def copy(serverEndpoints: List[ServerEndpoint[Any, F]]) = new SNUnitServerBuilder(
    serverEndpoints = serverEndpoints
  )

  def withServerEndpoints(serverEndpoints: List[ServerEndpoint[Any, F]]): SNUnitServerBuilder[F] = {
    copy(serverEndpoints = serverEndpoints)
  }

  def run: F[Unit] = for
    shutdownDeferred <- Deferred[IO, IO[Unit]].to[F]
    pollers <- IO.pollers.to[F]
    shutdown <- Dispatcher.parallel[F](await = true).use { dispatcher =>
      for
        handler <- new SNUnitCatsServerInterpreter[F](dispatcher).toHandler(serverEndpoints)
        _ <- snunit.CEAsyncServerBuilder
          .setDispatcher(dispatcher)
          .setFileDescriptorPoller(pollers.head.asInstanceOf)
          .setShutdownDeferred(shutdownDeferred)
          .setRequestHandler(handler)
          .build
          .to[F]
        shutdown <- shutdownDeferred.get.to[F]
      yield shutdown
    }
    _ <- shutdown.to[F]
  yield ()
}

object SNUnitServerBuilder {
  def default[F[_]: Async: LiftIO]: SNUnitServerBuilder[F] = {
    new SNUnitServerBuilder[F](
      serverEndpoints = endpoint.out(statusCode(StatusCode.NotFound)).serverLogicSuccess(_ => Async[F].pure(())) :: Nil
    )
  }
}