package kyo

import kyo.kernel.*
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import zio.Exit
import zio.Runtime
import zio.Unsafe
import zio.ZIO
import zio.stacktracer.TracingImplicits.disableAutoTrace

object ZIOs:
    import internal.*

    /** Lifts a zio.ZIO into a Kyo effect.
      *
      * @param zio
      *   The zio.ZIO to lift
      * @return
      *   A Kyo effect that, when run, will execute the zio.ZIO
      */
    def get[E, A](v: => ZIO[Any, E, A])(using Frame, zio.Trace): A < (Abort[E] & Async) =
        IO.Unsafe {
            Unsafe.unsafely {
                import zio.unsafeInterrupt
                given ce: CanEqual[E, E] = CanEqual.derived
                val p                    = Promise.Unsafe.init[E, A]()
                val f                    = Runtime.default.unsafe.fork(v)
                f.unsafe.addObserver { (exit: zio.Exit[E, A]) =>
                    p.completeDiscard(exit.toResult)
                }
                p.onInterrupt(_ => discard(f.unsafeInterrupt()))
                p.safe.get
            }
        }
    end get

    /** Placeholder for ZIO effects with environments (currently not supported).
      *
      * @tparam R
      *   The environment type
      * @tparam E
      *   The error type
      * @tparam A
      *   The result type
      */
    inline def get[R: zio.Tag, E, A](v: ZIO[R, E, A])(using Tag[Env[R]], Frame, zio.Trace): A < (Env[R] & Abort[E] & Async) =
        compiletime.error("ZIO environments are not supported yet. Please handle them before calling this method.")

    /** Interprets a Kyo computation to ZIO. Note that this method only accepts Abort[E] and Async pending effects. Plase handle any other
      * effects before calling this method.
      *
      * @param v
      *   The Kyo effect to run
      * @return
      *   A zio.ZIO that, when run, will execute the Kyo effect
      */
    def run[A: Flat, E](v: => A < (Abort[E] & Async))(using frame: Frame, trace: zio.Trace): ZIO[Any, E, A] =
        ZIO.suspendSucceed {
            import AllowUnsafe.embrace.danger
            Async.run(v).map { fiber =>
                ZIO.asyncInterrupt[Any, E, A] { cb =>
                    fiber.unsafe.onComplete {
                        case Result.Success(a) => cb(Exit.succeed(a))
                        case Result.Fail(e)    => cb(Exit.fail(e))
                        case Result.Panic(e)   => cb(Exit.die(e))
                    }
                    Left(ZIO.succeed {
                        fiber.unsafe.interrupt(Result.Panic(Fiber.Interrupted(frame)))
                    })
                }
            }.pipe(IO.Unsafe.run).eval
        }
    end run

    extension [E, A](exit: zio.Exit[E, A])
        /** Converts a zio.Exit to a kyo.Result.
          *
          * @return
          *   A kyo.Result
          */
        def toResult(using Frame, CanEqual[E, E]): Result[E, A] =
            exit match
                case Exit.Success(a)     => Result.success(a)
                case Exit.Failure(cause) => cause.toError

    extension [E](cause: zio.Cause[E])
        /** Converts a zio.Cause to a kyo.Result.Error.
          *
          * This method recursively traverses the zio.Cause structure and converts it to the appropriate kyo.Result.Error type.
          *
          * @return
          *   A kyo.Result.Error
          */
        def toError(using frame: Frame, ce: CanEqual[E, E]): Result.Error[E] =
            import zio.Cause.*
            def loop(cause: zio.Cause[E]): Maybe[Result.Error[E]] =
                cause match
                    case Fail(e, trace)            => Maybe(Result.Fail(e))
                    case Die(e, trace)             => Maybe(Result.Panic(e))
                    case Interrupt(fiberId, trace) => Maybe(Result.Panic(Fiber.Interrupted(frame)))
                    case Then(left, right)         => loop(left).orElse(loop(right))
                    case Both(left, right)         => loop(left).orElse(loop(right))
                    case Stackless(e, trace)       => loop(e)
                    case _: Empty.type             => Maybe.empty
            loop(cause).getOrElse(Result.Panic(new Exception("Unexpected zio.Cause.Empty at " + frame.parse.position)))
        end toError
    end extension

end ZIOs
