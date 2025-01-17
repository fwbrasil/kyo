package kyo

import kyo.internal.BaseKyoKernelTest
import kyo.kernel.Platform
import org.scalatest.Assertion
import org.scalatest.NonImplicitAssertions
import org.scalatest.freespec.AsyncFreeSpec
import scala.compiletime.testing.typeCheckErrors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

abstract class Test extends AsyncFreeSpec with NonImplicitAssertions with BaseKyoKernelTest[Any]:

    def run(v: Future[Assertion] < Any): Future[Assertion] = v.eval

    type Assertion = org.scalatest.Assertion
    def assertionSuccess              = succeed
    def assertionFailure(msg: String) = fail(msg)

    override given executionContext: ExecutionContext = Platform.executionContext
end Test
