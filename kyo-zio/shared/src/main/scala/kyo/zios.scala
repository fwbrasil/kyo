package kyo

import ZIOs.internal.*
import kyo.kernel.*
import scala.util.control.NonFatal
import scala.util.control.NoStackTrace
import zio.Cause
import zio.Task
import zio.ZIO

opaque type ZIOs <: Async = GetZIO & Async

object ZIOs:

    def get[E >: Nothing: Tag, A](v: ZIO[Any, E, A])(using Frame): A < (Abort[E] & ZIOs) =
        val task = v.fold(Result.fail, Result.success)
        Effect.suspendMap(Tag[GetZIO], task)(Abort.get(_))

    def get[A](v: ZIO[Any, Nothing, A])(using Frame): A < ZIOs =
        Effect.suspend(Tag[GetZIO], v)

    inline def get[R: zio.Tag, E, A](v: ZIO[R, E, A])(using Tag[Env[R]], Frame): A < (Env[R] & ZIOs) =
        compiletime.error("ZIO environments are not supported yet. Please handle them before calling this method.")

    def run[E, A](v: A < (Abort[E] & ZIOs))(using Frame): ZIO[Any, E, A] =
        ZIO.suspendSucceed {
            try
                Effect.handle(Tag[GetZIO], v.map(r => ZIO.succeed(r): ZIO[Any, E, A]))(
                    [C] => (input, cont) => input.flatMap(r => run(cont(r)).flatten)
                ).pipe(Async.run).map { fiber =>
                    ZIO.asyncInterrupt[Any, E, A] { cb =>
                        fiber.unsafe.onComplete {
                            case Result.Fail(ex)   => cb(ZIO.fail(ex))
                            case Result.Panic(ex)  => cb(ZIO.die(ex))
                            case Result.Success(v) => cb(v)
                        }
                        Left(ZIO.succeed {
                            fiber.unsafe.interrupt(interrupt)
                        })
                    }
                }.pipe(IO.run).eval
            catch
                case ex if NonFatal(ex) =>
                    ZIO.die(ex)
        }
    end run

    private[kyo] object internal:
        class ZIOsInterrupt extends NoStackTrace
        val interrupt = Result.Panic(new ZIOsInterrupt)
        sealed trait GetZIO extends Effect[ZIO[Any, Nothing, *], Id]
    end internal
end ZIOs
