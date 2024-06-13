package kyo

import kyo.*
import kyoTest.KyoTest
import org.scalatest.compatible.Assertion
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class iosTest extends KyoTest:

    "lazyRun" - {
        "execution" in run {
            var called = false
            val v =
                IOs {
                    called = true
                    1
                }
            assert(!called)
            assert(IOs.runLazy(v).pure == 1)
            assert(called)
        }
        "next handled effects can execute" in run {
            var called = false
            val v =
                Envs.get[Int].map { i =>
                    IOs {
                        called = true
                        i
                    }
                }
            assert(!called)
            val v2 = IOs.runLazy(v)
            assert(!called)
            assert(
                Envs.run(1)(v2).pure ==
                    1
            )
            assert(called)
        }
        "failure" in run {
            val ex        = new Exception
            def fail: Int = throw ex

            val ios = List(
                IOs(fail),
                IOs(fail).map(_ + 1),
                IOs(1).map(_ => fail),
                IOs(IOs(1)).map(_ => fail)
            )
            ios.foreach { io =>
                assert(Try(IOs.runLazy(io)) == Try(fail))
            }
            succeed
        }
        "stack-safe" in run {
            val frames = 10000
            def loop(i: Int): Int < IOs =
                IOs {
                    if i < frames then
                        loop(i + 1)
                    else
                        i
                }
            assert(
                IOs.runLazy(loop(0)).pure ==
                    frames
            )
        }
    }
    "run" - {
        "execution" in run {
            var called = false
            val v: Int < IOs =
                IOs {
                    called = true
                    1
                }
            assert(!called)
            assert(
                IOs.run[Int](v) ==
                    1
            )
            assert(called)
        }
        "stack-safe" in run {
            val frames = 100000
            def loop(i: Int): Assertion < IOs =
                IOs {
                    if i < frames then
                        loop(i + 1)
                    else
                        succeed
                }
            loop(0)
        }
        "failure" in run {
            val ex        = new Exception
            def fail: Int = throw ex

            val ios = List(
                IOs(fail),
                IOs(fail).map(_ + 1),
                IOs(1).map(_ => fail),
                IOs(IOs(1)).map(_ => fail)
            )
            ios.foreach { io =>
                assert(Try(IOs.run(io)) == Try(fail))
            }
            succeed
        }
        "doesn't accept other pending effects" in {
            assertDoesNotCompile("IOs.run[Int < Options](Options.get(Some(1)))")
        }
    }

    "ensure" - {
        "success" in {
            var called = false
            assert(
                IOs.toTry(IOs.run(IOs.ensure { called = true }(1))).pure ==
                    Try(1)
            )
            assert(called)
        }
        "failure" in {
            val ex     = new Exception
            var called = false
            assert(
                IOs.toTry(IOs.run(IOs.ensure { called = true } {
                    IOs[Int, Any](throw ex)
                })).pure ==
                    Failure(ex)
            )
            assert(called)
        }
    }

    val e = new Exception

    "toTry" - {
        "failure" in {
            assert(
                IOs.run(IOs.toTry((throw e): Int)) ==
                    Failure(e)
            )
        }
        "success" in {
            assert(
                IOs.run(IOs.toTry(1)) ==
                    Success(1)
            )
        }
    }

    "fromTry" - {
        "failure" in {
            assert(
                IOs.run(IOs.toTry(IOs.fromTry(Failure[Int](e)))) ==
                    Failure(e)
            )
        }
        "success" in {
            assert(
                IOs.run(IOs.toTry(IOs.fromTry(Success(1)))) ==
                    Success(1)
            )
        }
    }

    "fail" in {
        assert(
            IOs.run(IOs.toTry(IOs.fail[Int](e))) ==
                Failure(e)
        )
    }

    "failures" - {
        "handle" in {
            assert(
                IOs.run(IOs.toTry(1: Int < IOs)) ==
                    Try(1)
            )
        }
        "handle + transform" in {
            assert(
                IOs.run(IOs.toTry((1: Int < IOs).map(_ + 1))) ==
                    Try(2)
            )
        }
        "handle + effectful transform" in {
            assert(
                IOs.run(IOs.toTry((1: Int < IOs).map(i => IOs.fromTry(Try(i + 1))))) ==
                    Try(2)
            )
        }
        "handle + transform + effectful transform" in {
            assert(
                IOs.run(IOs.toTry((1: Int < IOs).map(_ + 1).map(i => IOs.fromTry(Try(i + 1))))) ==
                    Try(3)
            )
        }
        "handle + transform + failed effectful transform" in {
            val e = new Exception
            assert(
                IOs.run(
                    IOs.toTry((1: Int < IOs).map(_ + 1).map(i => IOs.fromTry(Try[Int](throw e))))
                ) ==
                    Failure(e)
            )
        }
    }

    "effectful" - {
        "handle" in {
            assert(
                IOs.run(IOs.toTry(IOs.fromTry(Try(1)))) ==
                    Try(1)
            )
        }
        "handle + transform" in {
            assert(
                IOs.run(IOs.toTry(IOs.fromTry(Try(1)).map(_ + 1))) ==
                    Try(2)
            )
        }
        "handle + effectful transform" in {
            assert(
                IOs.run(IOs.toTry(IOs.fromTry(Try(1)).map(i => IOs.fromTry(Try(i + 1))))) ==
                    Try(2)
            )
        }
        "handle + transform + effectful transform" in {
            assert(
                IOs.run(
                    IOs.toTry((IOs.fromTry(Try(1))).map(_ + 1).map(i => IOs.fromTry(Try(i + 1))))
                ) ==
                    Try(3)
            )
        }
        "handle + failed transform" in {
            assert(
                IOs.run(IOs.toTry((IOs.fromTry(Try(1))).map(_ => (throw e): Int))) ==
                    Failure(e)
            )
        }
        "handle + transform + effectful transform + failed transform" in {
            assert(
                IOs.run(IOs.toTry((IOs.fromTry(Try(1))).map(_ + 1).map(i =>
                    IOs.fromTry(Try(i + 1))
                ).map(_ =>
                    (throw e): Int
                ))) ==
                    Failure(e)
            )
        }
        "handle + transform + failed effectful transform" in {
            assert(
                IOs.run(IOs.toTry((IOs.fromTry(Try(1))).map(_ + 1).map(i =>
                    IOs.fromTry(Try((throw e): Int))
                ))) ==
                    Failure(e)
            )
        }
        "nested effect + failure" in {
            assert(
                IOs.run(
                    Options.run(
                        IOs.toTry[Int, Options & IOs](
                            IOs.fromTry(Try(Option(1))).map(opt =>
                                Options.get(opt: Option[Int] < IOs).map(_ => (throw e): Int)
                            )
                        )
                    )
                ) ==
                    Some(Failure(e))
            )
        }
    }
end iosTest
