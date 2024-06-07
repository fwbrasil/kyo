package kyoTest

import kyo.*
import kyo.internal.Trace
import scala.util.Try

class directTest extends KyoTest:

    // "match" in {
    //   val a = IOs(1)
    //   val b = IOs(2)
    //   val c =
    //     defer {
    //       await(a) match {
    //         case i if (await(b) > 0) => i
    //         case 2                   => 99
    //       }
    //     }
    //   assert(IOs.run(c) == 1)
    // }

    "one run" in {
        val io = defer {
            val a = await(IOs("hello"))
            a + " world"
        }
        assert(IOs.run(io) == "hello world")
    }

    "two runs" in {
        val io =
            defer {
                val a = await(IOs("hello"))
                val b = await(IOs("world"))
                a + " " + b
            }
        assert(IOs.run(io) == "hello world")
    }

    "two effects" in {
        val io: String < (IOs & Options) =
            defer {
                val a = await(Options.get(Some("hello")))
                val b = await(IOs("world"))
                a + " " + b
            }
        assert(IOs.run(Options.run(io)) == Some("hello world"))
    }

    "if" in {
        var calls = Seq.empty[Int]
        val io: Boolean < IOs =
            defer {
                if await(IOs { calls :+= 1; true }) then
                    await(IOs { calls :+= 2; true })
                else
                    await(IOs { calls :+= 3; true })
            }
        assert(IOs.run(io))
        assert(calls == Seq(1, 2))
    }

    "booleans" - {
        "&&" in {
            var calls = Seq.empty[Int]
            val io: Boolean < IOs =
                defer {
                    (await(IOs { calls :+= 1; true }) && await(IOs { calls :+= 2; true }))
                }
            assert(IOs.run(io))
            assert(calls == Seq(1, 2))
        }
        "||" in {
            var calls = Seq.empty[Int]
            val io: Boolean < IOs =
                defer {
                    (await(IOs { calls :+= 1; true }) || await(IOs { calls :+= 2; true }))
                }
            assert(IOs.run(io))
            assert(calls == Seq(1))
        }
    }

    "while" in {
        val io =
            defer {
                val c = await(Atomics.initInt(1))
                while await(c.get) < 100 do
                    await(c.incrementAndGet)
                    ()
                await(c.get)
            }
        assert(IOs.run(io) == 100)
    }

    "options" in {
        def test(opt: Option[Int]) =
            assert(opt == Options.run(defer(await(Options.get(opt)))).pure)
        test(Some(1))
        test(None)
    }
    "tries" in {
        def test(t: Try[Int]) =
            assert(t == IOs.run(IOs.toTry(defer(await(IOs.fromTry(t))))))
        test(Try(1))
        test(Try(throw new Exception("a")))
    }
    "consoles" in {
        object console extends Console:

            def printErr(s: String)(using Trace): Unit < IOs = ???

            def println(s: String)(using Trace): Unit < IOs = ???

            def print(s: String)(using Trace): Unit < IOs = ???

            def readln(using Trace): String < IOs = "hello"

            def printlnErr(s: String)(using Trace): Unit < IOs = ???
        end console
        val io: String < IOs = Consoles.run(console)(defer(await(Consoles.readln)))
        assert(IOs.run(io) == "hello")
    }

    "kyo computations must be within a run block" in {
        assertDoesNotCompile("defer(IOs(1))")
        assertDoesNotCompile("""
            defer {
                val a = IOs(1)
                10
            }
        """)
        assertDoesNotCompile("""
            defer {
                val a = {
                val b = IOs(1)
                10
                }
                10
            }
        """)
    }

    "choices" in {

        val x = Choices.get(Seq(1, -2, -3))
        val y = Choices.get(Seq("ab", "cde"))

        val v: Int < Choices =
            defer {
                val xx = await(x)
                xx + (
                    if xx > 0 then await(y).length * await(x)
                    else await(y).length
                )
            }

        val a: Seq[Int] = Choices.run(v).pure
        assert(a == Seq(3, -3, -5, 4, -5, -8, 0, 1, -1, 0))
    }

    "choices + filter" in {

        val x = Choices.get(Seq(1, -2, -3))
        val y = Choices.get(Seq("ab", "cde"))

        val v: Int < Choices =
            defer {
                val xx = await(x)
                val r =
                    xx + (
                        if xx > 0 then await(y).length * await(x)
                        else await(y).length
                    )
                await(Choices.filter(r > 0))
                r
            }

        assert(Choices.run(v).pure == Seq(3, 4, 1))
    }
end directTest
