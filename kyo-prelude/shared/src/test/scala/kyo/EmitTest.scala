package kyo

import kyo.Emit.Ack.*

class EmitTest extends Test:

    "int" in {
        val v: String < Emit[Int] =
            for
                _ <- Emit(1)
                _ <- Emit(1)
                _ <- Emit(1)
            yield "a"

        assert(Emit.run(v).eval == (Chunk(1, 1, 1), "a"))
    }
    "string" in {
        val v: String < Emit[String] =
            for
                _ <- Emit("1")
                _ <- Emit("2")
                _ <- Emit("3")
            yield "a"
        val res = Emit.run(v)
        assert(res.eval == (Chunk("1", "2", "3"), "a"))
    }
    "int and string" in {
        val v: String < (Emit[Int] & Emit[String]) =
            for
                _ <- Emit(3)
                _ <- Emit("1")
                _ <- Emit(2)
                _ <- Emit("2")
                _ <- Emit(1)
                _ <- Emit("3")
            yield "a"
        val res: (Chunk[String], (Chunk[Int], String)) =
            Emit.run(Emit.run[Int](v)).eval
        assert(res == (Chunk("1", "2", "3"), (Chunk(3, 2, 1), "a")))
    }

    "no values" in {
        val t = Emit.run[Int]("a").eval
        assert(t == (Chunk.empty, "a"))

        val t2 = Emit.run[String](42).eval
        assert(t2 == (Chunk.empty, 42))
    }

    "List" in {
        val v =
            for
                _ <- Emit(List(1))
                _ <- Emit(List(2))
                _ <- Emit(List(3))
            yield "a"
        val res = Emit.run(v)
        assert(res.eval == (Chunk(List(1), List(2), List(3)), "a"))
    }

    "Set" in {
        val v =
            for
                _ <- Emit(Set(1))
                _ <- Emit(Set(2))
                _ <- Emit(Set(3))
            yield "a"
        val res = Emit.run(v)
        assert(res.eval == (Chunk(Set(1), Set(2), Set(3)), "a"))
    }

    "Ack" - {
        "apply" - {
            "negative" in {
                val ack = Emit.Ack(-1)
                assert(ack == Emit.Ack.Stop)
            }
            "zero" in {
                val ack = Emit.Ack(0)
                assert(ack == Emit.Ack.Stop)
            }
            "positive" in {
                Emit.Ack(1) match
                    case Continue(n) => assert(n == 1)
                    case Stop        => fail()
            }
        }
        "maxItems" - {
            "stop with negative" in {
                val ack = Emit.Ack.Stop.maxValues(-1)
                assert(ack == Emit.Ack.Stop)
            }
            "stop with zero" in {
                val ack = Emit.Ack.Stop.maxValues(0)
                assert(ack == Emit.Ack.Stop)
            }
            "stop with positive" in {
                val ack = Emit.Ack.Stop.maxValues(1)
                assert(ack == Emit.Ack.Stop)
            }
            "continue with negative" in {
                val ack = Emit.Ack(2).maxValues(-1)
                assert(ack == Emit.Ack.Stop)
            }
            "continue with zero" in {
                val ack = Emit.Ack(2).maxValues(0)
                assert(ack == Emit.Ack.Stop)
            }
            "continue with less" in {
                val ack = Emit.Ack(2).maxValues(1)
                assert(ack == Emit.Ack(1))
            }
            "continue with more" in {
                val ack = Emit.Ack(2).maxValues(3)
                assert(ack == Emit.Ack(2))
            }
        }
        "Continue" - {
            "unapply" - {
                "stop" in {
                    val res = Emit.Ack.Continue.unapply(Emit.Ack.Stop)
                    assert(res.isEmpty)
                }
                "continue" in {
                    val res = Emit.Ack.Continue.unapply(Emit.Ack(1))
                    assert(res.get == 1)
                }
            }
        }
        "next" - {
            "stop short circuits" in run {
                var executed = false
                Emit.Ack.Stop.next {
                    executed = true
                    Emit.Ack.Continue()
                }.map { ack =>
                    assert(ack == Emit.Ack.Stop)
                    assert(!executed)
                }
            }

            "continue executes function" in run {
                var executed = false
                Emit.Ack(2).next {
                    executed = true
                    Emit.Ack(1)
                }.map { ack =>
                    assert(ack == Emit.Ack(1))
                    assert(executed)
                }
            }
        }
        "Flat" in {
            summon[Flat[Emit.Ack]]
            summon[Flat[Emit.Ack.Continue]]
            summon[Flat[Emit.Ack.Stop.type]]
            succeed
        }
    }

    "runAck" - {
        "runAck" - {
            "with pure function" in {
                var seen = List.empty[Int]
                def emits(i: Int): Unit < Emit[Int] =
                    if i == 5 then ()
                    else
                        Emit.andMap(i) {
                            case Stop        => ()
                            case Continue(_) => emits(i + 1)
                        }

                Emit.runAck(emits(0)) { v =>
                    seen :+= v
                    if v < 3 then Emit.Ack.Continue()
                    else Emit.Ack.Stop
                }.eval
                assert(seen == List(0, 1, 2, 3))
            }

            "with effects" in {
                var seen = List.empty[Int]
                def emits(i: Int): Unit < Emit[Int] =
                    if i == 5 then ()
                    else
                        Emit.andMap(i) {
                            case Stop        => ()
                            case Continue(_) => emits(i + 1)
                        }

                val result =
                    Env.run(3) {
                        Var.runTuple(0) {
                            Emit.runAck(emits(0)) { v =>
                                for
                                    threshold <- Env.get[Int]
                                    count     <- Var.get[Int]
                                    _         <- Var.set(count + 1)
                                    _ = seen :+= v
                                yield if v <= threshold then Emit.Ack.Continue() else Emit.Ack.Stop
                            }
                        }
                    }.eval
                assert(seen == List(0, 1, 2, 3, 4))
                assert(result == (5, ()))
            }

            "early termination" in {
                var seen = List.empty[Int]
                def emits(i: Int): Unit < Emit[Int] =
                    if i == 5 then ()
                    else
                        Emit.andMap(i) {
                            case Stop        => ()
                            case Continue(_) => emits(i + 1)
                        }

                val result = Abort.run[String] {
                    Emit.runAck(emits(0)) { v =>
                        seen :+= v
                        if v < 3 then Emit.Ack.Continue()
                        else Abort.fail("Reached 3")
                    }
                }.eval
                assert(seen == List(0, 1, 2, 3))
                assert(result == Result.fail("Reached 3"))
            }
        }
    }

    "runFold" - {
        "basic folding" in {
            val v =
                for
                    _ <- Emit(1)
                    _ <- Emit(2)
                    _ <- Emit(3)
                yield "a"

            val result = Emit.runFold(0)((acc, v: Int) => (acc + v, Emit.Ack.Continue()))(v).eval
            assert(result == (6, "a"))
        }

        "early termination" in {
            val v =
                for
                    _ <- Emit(1)
                    _ <- Emit(2)
                    _ <- Emit(3)
                    _ <- Emit(4)
                yield "a"

            val result = Emit.runFold(0)((acc, v: Int) =>
                if v < 3 then (acc + v, Emit.Ack.Continue())
                else (acc, Emit.Ack.Stop)
            )(v).eval
            assert(result == (3, "a"))
        }

        "with effects" in {
            val v =
                for
                    _ <- Emit(1)
                    _ <- Emit(2)
                    _ <- Emit(3)
                yield "a"

            val result = Env.run(10) {
                Emit.runFold(0)((acc, v: Int) =>
                    for
                        threshold <- Env.get[Int]
                        newAcc = if v < threshold then acc + v else acc
                    yield (newAcc, Emit.Ack.Continue())
                )(v)
            }.eval
            assert(result == (6, "a"))
        }
    }

    "runDiscard" - {
        "discards all values" in {
            val v =
                for
                    _ <- Emit(1)
                    _ <- Emit(2)
                    _ <- Emit(3)
                yield "a"

            val result = Emit.runDiscard(v).eval
            assert(result == "a")
        }

        "with effects" in {
            var count = 0
            val v =
                for
                    _ <- Emit(1)
                    _ <- Emit(2)
                    _ <- Emit(3)
                yield count

            val result = Emit.runDiscard(v).eval
            assert(result == 0)
            assert(count == 0) // Ensures values were truly discarded
        }
    }

    "generic type parameters" - {
        "single generic emit" in {
            def emitGeneric[T: Tag](value: T): Unit < Emit[T] =
                Emit(value).unit

            val result = Emit.run(emitGeneric(42)).eval
            assert(result == (Chunk(42), ()))
        }

        "multiple generic emits" in {
            def emitMultipleGeneric[T: Tag, U: Tag](t: T, u: U): Unit < (Emit[T] & Emit[U]) =
                for
                    _ <- Emit(t)
                    _ <- Emit(u)
                yield ()

            val result = Emit.run[Int](Emit.run[String](emitMultipleGeneric(42, "hello"))).eval
            assert(result == (Chunk(42), (Chunk("hello"), ())))
        }

        "nested generic emits" in {
            def nestedEmit[T: Tag, U: Tag](t: T, u: U): Unit < (Emit[T] & Emit[U] & Emit[(T, U)]) =
                for
                    _ <- Emit(t)
                    _ <- Emit(u)
                    _ <- Emit((t, u))
                yield ()

            val result = Emit.run[Int](Emit.run[String](Emit.run[(Int, String)](nestedEmit(42, "world")))).eval
            assert(result == (Chunk(42), (Chunk("world"), (Chunk((42, "world")), ()))))
        }

        "multiple generic emits with different types" in {
            def multiEmit[T: Tag, U: Tag, V: Tag](t: T, u: U, v: V): Unit < (Emit[T] & Emit[U] & Emit[V]) =
                for
                    _ <- Emit(t)
                    _ <- Emit(u)
                    _ <- Emit(v)
                yield ()

            val result = Emit.run[Int](Emit.run[String](Emit.run[Boolean](multiEmit(42, "test", true)))).eval
            assert(result == (Chunk(42), (Chunk("test"), (Chunk(true), ()))))
        }
    }

    "different emit tags with same object instance" - {
        trait A derives CanEqual
        trait B derives CanEqual
        object C extends A with B

        "two emitters with same object" in {
            def emitC: Unit < (Emit[A] & Emit[B]) =
                for
                    _ <- Emit[A](C)
                    _ <- Emit[B](C)
                yield ()

            val result = Emit.run[A] {
                Emit.run[B] {
                    emitC
                }
            }.eval

            assert(result._1 == Chunk(C: A))
            assert(result._2._1 == Chunk(C))
            assert(result._2._2 == ())
        }

        "multiple emissions with different tags" in {
            trait X derives CanEqual
            trait Y derives CanEqual
            case class D(value: Int) extends X with Y

            def emitMultiple(values: List[D]): Unit < (Emit[X] & Emit[Y]) =
                Kyo.foreachDiscard(values) { d =>
                    for
                        _ <- Emit[X](d)
                        _ <- Emit[Y](d)
                    yield ()
                }

            val data = List(D(1), D(2), D(3))

            val result = Emit.run[X] {
                Emit.run[Y] {
                    emitMultiple(data)
                }
            }.eval

            assert(result._1 == Chunk(D(1), D(2), D(3)))
            assert(result._2._1 == Chunk(D(1), D(2), D(3)))
            assert(result._2._2 == ())
        }

        "complex scenario with multiple tags" in {
            trait P derives CanEqual
            trait Q derives CanEqual
            trait R derives CanEqual
            case class E(value: String) extends P with Q with R

            def complexEmit(values: List[E]): Unit < (Emit[P] & Emit[Q] & Emit[R]) =
                Kyo.foreachDiscard(values) { e =>
                    for
                        _ <- Emit[P](e)
                        _ <- Emit[Q](e)
                        _ <- Emit[R](e)
                    yield ()
                }

            val data = List(E("one"), E("two"), E("three"))

            val result = Emit.run[P] {
                Emit.run[Q] {
                    Emit.run[R] {
                        complexEmit(data)
                    }
                }
            }.eval

            assert(result._1 == Chunk(E("one"), E("two"), E("three")))
            assert(result._2._1 == Chunk(E("one"), E("two"), E("three")))
            assert(result._2._2._1 == Chunk(E("one"), E("two"), E("three")))
            assert(result._2._2._2 == ())
        }
    }

    "isolate" - {
        "merge" - {
            "combines emitted values from isolated and outer scopes" in run {
                val result = Emit.run {
                    for
                        _ <- Emit(1)
                        isolated <- Emit.isolate.merge[Int].run {
                            for
                                _ <- Emit(2)
                                _ <- Emit(3)
                            yield "inner"
                        }
                        _ <- Emit(4)
                    yield (isolated)
                }
                assert(result.eval == (Chunk(1, 2, 3, 4), "inner"))
            }

            "proper state restoration after nested isolations" in run {
                val result = Emit.run {
                    for
                        _ <- Emit("start")
                        v1 <- Emit.isolate.merge[String].run {
                            for
                                _ <- Emit("inner1")
                                v2 <- Emit.isolate.merge[String].run {
                                    Emit("nested").map(_ => "nested-result")
                                }
                            yield v2
                        }
                        _ <- Emit("end")
                    yield v1
                }
                assert(result.eval == (Chunk("start", "inner1", "nested", "end"), "nested-result"))
            }
        }

        "discard" - {
            "inner emissions don't affect outer scope" in run {
                val result = Emit.run {
                    for
                        _ <- Emit(1)
                        isolated <- Emit.isolate.discard[Int].run {
                            for
                                _ <- Emit(2)
                                _ <- Emit(3)
                            yield "inner"
                        }
                        _ <- Emit(4)
                    yield isolated
                }
                assert(result.eval == (Chunk(1, 4), "inner"))
            }

            "nested discards maintain isolation" in run {
                val result = Emit.run {
                    for
                        _ <- Emit("outer")
                        v1 <- Emit.isolate.discard[String].run {
                            for
                                _ <- Emit("discarded1")
                                v2 <- Emit.isolate.discard[String].run {
                                    Emit("discarded2").map(_ => "nested-result")
                                }
                            yield v2
                        }
                        _ <- Emit("final")
                    yield v1
                }
                assert(result.eval == (Chunk("outer", "final"), "nested-result"))
            }
        }

        "composition" - {
            "can combine with Var isolate" in run {
                val emitIsolate = Emit.isolate.merge[Int]
                val varIsolate  = Var.isolate.discard[Int]

                val combined = emitIsolate.andThen(varIsolate)

                val result = Emit.run {
                    Var.runTuple(0) {
                        combined.run {
                            for
                                _ <- Emit(1)
                                _ <- Var.update[Int](_ + 1)
                                v <- Var.get[Int]
                                _ <- Emit(v)
                            yield "done"
                        }
                    }
                }
                assert(result.eval == (Chunk(1, 1), (0, "done")))
            }

            "can combine with Memo isolate" in run {
                var count = 0
                val f = Memo[Int, Int, Any] { x =>
                    count += 1
                    x * 2
                }

                val emitIsolate = Emit.isolate.merge[Int]
                val memoIsolate = Memo.isolate.merge

                val combined = emitIsolate.andThen(memoIsolate)

                val result = Emit.run {
                    Memo.run {
                        combined.run {
                            for
                                a <- f(1)
                                _ <- Emit(a)
                                b <- f(1)
                                _ <- Emit(b)
                            yield (a, b)
                        }
                    }
                }
                assert(result.eval == (Chunk(2, 2), (2, 2)))
                assert(count == 1)
            }

            "preserves individual isolation behaviors when composed" in run {
                val emitDiscard = Emit.isolate.discard[Int]
                val emitMerge   = Emit.isolate.merge[Int]

                val result = Emit.run {
                    for
                        _ <- Emit(1)
                        _ <- emitDiscard.run {
                            Emit(2)
                        }
                        _ <- Emit(3)
                        _ <- emitMerge.run {
                            Emit(4)
                        }
                        _ <- Emit(5)
                    yield "done"
                }
                assert(result.eval == (Chunk(1, 3, 4, 5), "done"))
            }
        }
    }
end EmitTest
