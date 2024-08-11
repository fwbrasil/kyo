package kyo

import Tagged.*
import java.time.Instant

class KyoAppTest extends Test:

    "main" in runJVM {
        val app = new KyoApp:
            run {
                for
                    _ <- Console.println(s"Starting with args [${args.mkString(", ")}]")
                yield "Exit!"
            }

        app.main(Array("arg1", "arg2"))
        succeed
    }
    "multiple runs" taggedAs jvmOnly in run {
        for
            ref <- AtomicInt.init(0)
            app = new KyoApp:
                run { ref.getAndIncrement }
                run { ref.getAndIncrement }
                run { ref.getAndIncrement }

            _    <- IO(app.main(Array.empty))
            runs <- ref.get
        yield assert(runs == 3)
    }
    "effects" taggedAs jvmOnly in {
        def run: Int < KyoApp.Effects =
            for
                _ <- Timer.scheduleAtFixedRate(1.second, 1.second)(())
                i <- Random.nextInt
                _ <- Console.println(s"$i")
                _ <- Clock.now
                _ <- Resource.ensure(())
                _ <- Async.run(())
            yield 1

        assert(KyoApp.run(Duration.Infinity)(run) == 1)
    }
    "failing effects" taggedAs jvmOnly in {
        def run: Unit < KyoApp.Effects =
            for
                _ <- Clock.now
                _ <- Random.nextInt
                _ <- Abort.fail(new RuntimeException("Aborts!"))
            yield ()

        KyoApp.attempt(Duration.Infinity)(run) match
            case Result.Fail(exception) => assert(exception.getMessage == "Aborts!")
            case _                      => fail("Unexpected Success...")
    }

    "effect mismatch" taggedAs jvmOnly in {
        assertDoesNotCompile("""
            new KyoApp:
                run(1: Int < Options)
        """)
    }

    "indirect effect mismatch" taggedAs jvmOnly in {
        assertDoesNotCompile("""
            new KyoApp:
                run(Choices.run(1: Int < Options))
        """)
    }

    "custom services" taggedAs jvmOnly in run {
        for
            instantRef <- AtomicRef.init(Instant.MAX)
            randomRef  <- AtomicRef.init("")
            testClock = new Clock.Service:
                override def now(using Frame): Instant < IO = Instant.EPOCH
            testRandom = new Random.Service:
                override def nextInt(using Frame): Int < IO = ???

                override def nextInt(exclusiveBound: Int)(using Frame): Int < IO = ???

                override def nextLong(using Frame): Long < IO = ???

                override def nextDouble(using Frame): Double < IO = ???

                override def nextBoolean(using Frame): Boolean < IO = ???

                override def nextFloat(using Frame): Float < IO = ???

                override def nextGaussian(using Frame): Double < IO = ???

                override def nextValue[T](seq: Seq[T])(using Frame): T < IO = ???

                override def nextValues[T](length: Int, seq: Seq[T])(using Frame): Seq[T] < IO = ???

                override def nextStringAlphanumeric(length: Int)(using Frame): String < IO = "FooBar"

                override def nextString(length: Int, chars: Seq[Char])(using Frame): String < IO = ???
                override def nextBytes(length: Int)(using Frame): Seq[Byte] < IO                 = ???

                override def shuffle[T](seq: Seq[T])(using Frame): Seq[T] < IO = ???

                override def unsafe = ???

            app = new KyoApp:
                override val log: Log.Unsafe        = Log.Unsafe.ConsoleLogger("ConsoleLogger")
                override val clock: Clock.Service   = testClock
                override val random: Random.Service = testRandom
                run {
                    for
                        _ <- Clock.now.map(i => instantRef.update(_ => i))
                        _ <- Random.nextStringAlphanumeric(0).map(s => randomRef.update(_ => s))
                        _ <- Log.info("info")
                    yield ()
                }
            _    <- IO(app.main(Array.empty))
            time <- instantRef.get
            rand <- randomRef.get
        yield
            assert(time eq Instant.EPOCH)
            assert(rand == "FooBar")
        end for
    }
end KyoAppTest
