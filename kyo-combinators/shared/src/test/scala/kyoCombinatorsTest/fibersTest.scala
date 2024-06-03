package KyoTest

import kyo.*
import kyoTest.KyoTest

class fibersTest extends KyoTest:

    "fibers" - {
        "construct" - {
            "should generate fibers effect from async" in {
                var state: Int = 0
                val effect = Kyo.async[Int]((continuation) =>
                    state = state + 1
                    continuation(state)
                )
                val handledEffect = IOs.run(Fibers.run(effect).map(_.toFuture)).pure
                handledEffect.map(v =>
                    assert(state == 1)
                    assert(v == 1)
                )
            }

            "should construct from Future" in {
                val future        = scala.concurrent.Future(100)
                val effect        = Kyo.fromFuture(future)
                val handledEffect = IOs.run(Fibers.run(effect).map(_.toFuture)).pure
                handledEffect.map(v =>
                    assert(v == 100)
                )
            }

            "should construct from Promise" in {
                val promise = scala.concurrent.Promise[Int]
                val effect  = Kyo.fromPromiseScala(promise)
                scala.concurrent.Future {
                    promise.complete(scala.util.Success(100))
                }
                val handledEffect = IOs.run(Fibers.run(effect).map(_.toFuture))
                handledEffect.pure.map(v => assert(v == 100))
            }

            "should construct from foreachPar" in {
                val effect        = Kyo.foreachPar(Seq(1, 2, 3))(v => v * 2)
                val handledEffect = IOs.run(Fibers.run(effect).map(_.toFuture)).pure
                handledEffect.map(v => assert(v == Seq(2, 4, 6)))
            }

            "should construct from traversePar" in {
                val effect        = Kyo.traversePar(Seq(IOs(1), IOs(2), IOs(3)))
                val handledEffect = IOs.run(Fibers.run(effect).map(_.toFuture)).pure
                handledEffect.map(v => assert(v == Seq(1, 2, 3)))
            }

            "should generate a fiber that doesn't complete using never" in {
                val effect = Kyo.never
                runJVM {
                    val handledEffect = IOs.run(Aborts.run[Throwable](
                        Aborts.catching[Throwable](Fibers.runAndBlock(1.seconds)(effect))
                    ))
                    assert(handledEffect.pure match
                        case Left(Fibers.Interrupted) => true
                        case _                        => false
                    )
                }
            }
        }

        "fork" - {
            "should fork a fibers effect" in {
                val effect       = Fibers.sleep(100.millis) *> 10
                val forkedEffect = effect.fork
                val joinedEffect = Fibers.get(forkedEffect)
                val handled      = IOs.run(Fibers.run(joinedEffect).map(_.toFuture)).pure
                handled.map(v => assert(v == 10))
            }

            "should join a forked effect" in {
                val effect       = Fibers.sleep(100.millis) *> 10
                val forkedEffect = Fibers.init(effect)
                val joinedEffect = forkedEffect.join
                val handled      = IOs.run(Fibers.run(joinedEffect).map(_.toFuture)).pure
                handled.map(v => assert(v == 10))
            }

            "should construct from type and use" in {
                val effect = Kyo.serviceWith[String](_.length)
                assert(Envs.run("value")(effect).pure == 5)
            }
        }

        "handle" - {
            "should provide" in {
                val effect: Int < Envs[String] = Envs.get[String].map(_.length)
                assert(effect.provide("value").pure == 5)
            }

            "should provide incrementally" in {
                val effect: Int < Envs[String & Int & Boolean & Char] =
                    Envs.get[String] *> Envs.get[Int] *> Envs.get[Boolean] *> Envs.get[Char].as(23)
                val handled =
                    effect
                        .provide('c')
                        .provide("value")
                        .provide(1)
                        .provide(false)
                assert(handled.pure == 23)
            }
        }

        "zip par" - {
            "should zip right par" in {
                val e1      = IOs(1)
                val e2      = IOs(2)
                val effect  = e1 &> e2
                val handled = IOs.run(Fibers.run(effect).map(_.toFuture)).pure
                handled.map(v =>
                    assert(v == 2)
                )
            }

            "should zip left par" in {
                val e1      = IOs(1)
                val e2      = IOs(2)
                val effect  = e1 <& e2
                val handled = IOs.run(Fibers.run(effect).map(_.toFuture)).pure
                handled.map(v =>
                    assert(v == 1)
                )
            }

            "should zip par" in {
                val e1      = IOs(1)
                val e2      = IOs(2)
                val effect  = e1 <&> e2
                val handled = IOs.run(Fibers.run(effect).map(_.toFuture)).pure
                handled.map(v =>
                    assert(v == (1, 2))
                )
            }
        }
    }

end fibersTest
