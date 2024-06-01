package kyoTest

import kyo.*
import kyo.internal.Trace

class consolesTest extends KyoTest:

    case class Obj(a: String)
    val obj       = Obj("a")
    val pprintObj = pprint.apply(obj).toString

    "readln" in IOs.run {
        val testConsole = new TestConsole
        testConsole.readlns = List("readln")
        val io: String < IOs = Consoles.run(testConsole)(Consoles.readln)
        assert(IOs.run(io) == "readln")
    }
    "print" in IOs.run {
        val testConsole = new TestConsole
        IOs.run(Consoles.run(testConsole)(Consoles.print("print")))
        assert(testConsole.prints == List("print"))
    }
    "printErr" in IOs.run {
        val testConsole = new TestConsole
        IOs.run(Consoles.run(testConsole)(Consoles.printErr("printErr")))
        assert(testConsole.printErrs == List("printErr"))
    }
    "println" in IOs.run {
        val testConsole = new TestConsole
        IOs.run(Consoles.run(testConsole)(Consoles.println("println")))
        assert(testConsole.printlns == List("println"))
    }
    "printlnErr" in IOs.run {
        val testConsole = new TestConsole
        IOs.run(Consoles.run(testConsole)(Consoles.printlnErr("printlnErr")))
        assert(testConsole.printlnErrs == List("printlnErr"))
    }

    class TestConsole extends Console:
        var readlns     = List.empty[String]
        var prints      = List.empty[String]
        var printErrs   = List.empty[String]
        var printlns    = List.empty[String]
        var printlnErrs = List.empty[String]

        def readln(using Trace): String < IOs =
            IOs {
                val v = readlns.head
                readlns = readlns.tail
                v
            }
        def print(s: String)(using Trace): Unit < IOs =
            IOs {
                prints ::= s
            }
        def printErr(s: String)(using Trace): Unit < IOs =
            IOs {
                printErrs ::= s
            }
        def println(s: String)(using Trace): Unit < IOs =
            IOs {
                printlns ::= s
            }
        def printlnErr(s: String)(using Trace): Unit < IOs =
            IOs {
                printlnErrs ::= s
            }
    end TestConsole
end consolesTest
