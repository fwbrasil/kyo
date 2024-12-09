package kyo

import scala.collection.mutable.ListBuffer

abstract class KyoAppPlatformSpecific extends KyoApp.Base[Async & Resource & Abort[Throwable]]:

    final override protected def run[A: Flat](v: => A < (Async & Resource & Abort[Throwable]))(using Frame): Unit =
        import AllowUnsafe.embrace.danger
        initCode = initCode.appended(() =>
            val result = IO.Unsafe.run(Abort.run(Async.runAndBlock(timeout)(handle(v)))).eval
            printResult(result)
        )
    end run

end KyoAppPlatformSpecific
