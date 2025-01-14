package snunit.tapir

import kyo.*
import kyo.internal.KyoSttpMonad

class SNUnitKyoInterpreter extends SNUnitGenericServerInterpreter:
    private[tapir] type Wrapper[T]     = KyoSttpMonad.M[T]
    private[tapir] type HandlerWrapper = KyoSttpMonad.M[snunit.RequestHandler]
    implicit private[tapir] val monadError = KyoSttpMonad

    private[tapir] val dispatcher = new WrapperDispatcher:
        inline def dispatch(f: => KyoSttpMonad.M[Unit]): Unit =
            import AllowUnsafe.embrace.danger
            IO.Unsafe.evalOrThrow(Async.run(f).unit)

    private[tapir] inline def createHandleWrapper(f: => snunit.RequestHandler): HandlerWrapper = IO(f)
    private[tapir] inline def wrapSideEffect[T](f: => T): Wrapper[T]                           = IO(f)
end SNUnitKyoInterpreter
