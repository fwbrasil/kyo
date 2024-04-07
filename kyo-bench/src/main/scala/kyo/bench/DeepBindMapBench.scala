package kyo.bench

class DeepBindMapBench extends Bench.SyncAndFork[Int]:

    val depth = 10000

    def kyoBench() =
        import kyo.*

        def loop(i: Int): Int < IOs =
            IOs {
                if i > depth then i
                else
                    IOs(i + 11)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(loop)
            }
        loop(0)
    end kyoBench

    def catsBench() =
        import cats.effect.*
        def loop(i: Int): IO[Int] =
            IO.unit.flatMap { _ =>
                if i > depth then
                    IO.pure(i)
                else
                    IO(i + 11)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .flatMap(loop)
            }
        loop(0)
    end catsBench

    def zioBench() =
        import zio.*
        def loop(i: Int): UIO[Int] =
            ZIO.unit.flatMap { _ =>
                if i > depth then
                    ZIO.succeed(i)
                else
                    ZIO.unit
                        .map(_ => (i + 11))
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .map(_ - 1)
                        .flatMap(loop)
            }
        loop(0)
    end zioBench
end DeepBindMapBench
