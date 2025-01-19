package kyo.internal

import java.util.concurrent.TimeoutException
import kyo.*
import kyo.kernel.Platform
import scala.annotation.targetName
import scala.concurrent.Future
import scala.util.Try

private[kyo] trait BaseKyoKernelTest[S] extends BaseKyoDataTest:

    def run(v: Future[Assertion] < S): Future[Assertion]

    @targetName("runAssertion")
    def run(v: Assertion < S): Future[Assertion] = run(v.map(Future.successful(_)))

    @targetName("runJVMAssertion")
    inline def runJVM(v: => Assertion < S): Future[Assertion] = runJVM(v.map(Future.successful(_)))

    @targetName("runJSAssertion")
    inline def runJS(v: => Assertion < S): Future[Assertion] = runJS(v.map(Future.successful(_)))

    @targetName("runNotJSAssertion")
    inline def runNotJS(v: => Assertion < S): Future[Assertion] = runNotJS(v.map(Future.successful(_)))

    @targetName("runNativeAssertion")
    inline def runNative(v: => Assertion < S): Future[Assertion] = runNative(v.map(Future.successful(_)))

    inline def runJVM(v: => Future[Assertion] < S): Future[Assertion] =
        if Platform.isJVM then
            run(v)
        else
            Future.successful(assertionSuccess)

    inline def runJS(v: => Future[Assertion] < S): Future[Assertion] =
        if Platform.isJS then
            run(v)
        else
            Future.successful(assertionSuccess)

    inline def runNotJS(v: => Future[Assertion] < S): Future[Assertion] =
        if !Platform.isJS then
            run(v)
        else
            Future.successful(assertionSuccess)

    inline def runNative(v: => Future[Assertion] < S): Future[Assertion] =
        if Platform.isNative then
            run(v)
        else
            Future.successful(assertionSuccess)

    def untilTrue[S](f: => Boolean < S): Boolean < S =
        def now: Duration = System.currentTimeMillis().millis
        val start         = now
        def loop(): Boolean < S =
            if now - start > timeout then
                throw new TimeoutException
            else
                f.map {
                    case true  => true
                    case false => loop()
                }
        loop()
    end untilTrue

    def timeout =
        if Platform.isDebugEnabled then
            Duration.Infinity
        else
            5.seconds
end BaseKyoKernelTest
