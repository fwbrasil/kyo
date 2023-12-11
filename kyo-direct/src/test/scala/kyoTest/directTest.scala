package kyoTest

import kyo._
import kyo.tries._
import kyo.options._
import kyo.direct._
import kyo.direct
import kyo.ios._
import kyo.envs._
import kyo.concurrent.fibers._
import scala.util.Try
import kyo.consoles._
import kyo.concurrent.atomics._
import kyo.direct._

class directTest extends KyoTest {

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
    val io: String < (IOs with Options) =
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
        if (await(IOs { calls :+= 1; true }))
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
        while (await(c.get) < 100) {
          await(c.incrementAndGet)
        }
        await(c.get)
      }
    assert(IOs.run(io) == 100)
  }

  "options" in {
    def test(opt: Option[Int]) =
      assert(opt == Options.run(defer(await(Options.get(opt)))))
    test(Some(1))
    test(None)
  }
  "tries" in {
    def test(t: Try[Int]) =
      assert(t == Tries.run(defer(await(Tries.get(t)))))
    test(Try(1))
    test(Try(throw new Exception("a")))
  }
  "consoles" in {
    object console extends Console {

      def printErr[T](s: => T): Unit < IOs = ???

      def println[T](s: => T): Unit < IOs = ???

      def print[T](s: => T): Unit < IOs = ???

      def readln: String < IOs = "hello"

      def printlnErr[T](s: => T): Unit < IOs = ???
    }
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

  "lists" in {
    import kyo.seqs._

    val x = Seqs.get(Seq(1, -2, -3))
    val y = Seqs.get(Seq("ab", "cde"))

    val v: Int < Seqs =
      defer {
        val xx = await(x)
        xx + (
            if (xx > 0) await(y).length * await(x)
            else await(y).length
        )
      }

    val a: Seq[Int] = Seqs.run(v).pure
    assert(a == Seq(3, -3, -5, 4, -5, -8, 0, 1, -1, 0))
  }

  "lists + filter" in {
    import kyo.seqs._

    val x = Seqs.get(Seq(1, -2, -3))
    val y = Seqs.get(Seq("ab", "cde"))

    val v: Int < Seqs =
      defer {
        val xx = await(x)
        val r =
          xx + (
              if (xx > 0) await(y).length * await(x)
              else await(y).length
          )
        await(Seqs.filter(r > 0))
        r
      }

    assert(Seqs.run(v).pure == Seq(3, 4, 1))
  }
}
