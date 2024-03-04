package kyo.bench

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

class BlockingCpuBench extends Bench.SyncAndFork[Unit]:

    def block() =
        Blackhole.consumeCPU(100000)
        Thread.sleep(1)

    def catsBench() =
        import cats.effect.*

        IO.blocking(block())
    end catsBench

    def kyoBench() =
        import kyo.*

        IOs(block())
    end kyoBench

    def zioBench() =
        import zio.*

        ZIO.blocking(ZIO.succeed(block()))
    end zioBench

    @Benchmark
    def syncOx(): Unit =
        block()

    @Benchmark
    def forkOx(): Unit =
        import ox.*
        scoped {
            fork(block()).join()
        }
    end forkOx

end BlockingCpuBench
