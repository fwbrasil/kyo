package kyo

class PoolTest extends Test:

    case class TestObj(id: Int)

    "handles failures during use" in run {
        for
            pool <- Pool.init[Int, IO](2)(IO(1))
            r    <- Abort.run(pool.use(_ => Abort.fail(new Exception("test"))))
            size <- pool.size
        yield assert(size == 1)
    }

    // "initialization" - {
    //     "creates pool with specified capacity" in run {
    //         for
    //             pool <- Pool.init[Int, IO](5)(IO(1))
    //             size <- pool.size
    //         yield assert(pool.capacity == 5 && size == 0)
    //     }
    // }

    // "object acquisition" - {
    //     "creates object when pool empty" in run {
    //         for
    //             counter <- AtomicInt.init(0)
    //             pool    <- Pool.init(5)(counter.incrementAndGet)
    //             result  <- pool.use(identity)
    //             size    <- pool.size
    //         yield assert(result == 1 && size == 1)
    //     }

    //     "reuses object from pool" in run {
    //         for
    //             counter <- AtomicInt.init(0)
    //             pool    <- Pool.init(5)(counter.incrementAndGet)
    //             v1      <- pool.use(identity)
    //             v2      <- pool.use(identity)
    //             count   <- counter.get
    //         yield assert(v1 == 1 && v2 == 1 && count == 1)
    //     }
    // }

    // "cleanup" - {
    //     "returns objects to pool after use" in run {
    //         for
    //             pool       <- Pool.init[Int, IO](2)(IO(1))
    //             _          <- pool.use(identity)
    //             beforeSize <- pool.size
    //             _          <- pool.use(identity)
    //             afterSize  <- pool.size
    //         yield assert(beforeSize == 1 && afterSize == 1)
    //     }

    //     "handles failures during use" in run {
    //         for
    //             pool <- Pool.init[Int, IO](2)(IO(1))
    //             _    <- Abort.run(pool.use(_ => Abort.fail(new Exception("test"))))
    //             size <- pool.size
    //         yield assert(size == 1)
    //     }

    //     "handles panics during use" in run {
    //         for
    //             pool <- Pool.init[Int, IO](2)(IO(1))
    //             _    <- Abort.run(pool.use(_ => throw new Exception("test")))
    //             size <- pool.size
    //         yield assert(size == 1)
    //     }
    // }

    // "close" - {
    //     "returns pooled objects" in run {
    //         for
    //             pool       <- Pool.init[Int, IO](2)(IO(1))
    //             _          <- pool.use(identity)
    //             pooledObjs <- pool.close
    //         yield assert(pooledObjs == Maybe(Seq(1)))
    //     }

    //     "returns empty for already closed pool" in run {
    //         for
    //             pool        <- Pool.init[Int, IO](2)(IO(1))
    //             _           <- pool.close
    //             closedAgain <- pool.close
    //         yield assert(closedAgain.isEmpty)
    //     }

    //     "fails operations after close" in run {
    //         for
    //             pool           <- Pool.init[Int, IO](2)(IO(1))
    //             _              <- pool.close
    //             useAfterClose  <- Abort.run(pool.use(identity))
    //             sizeAfterClose <- Abort.run(pool.size)
    //         yield assert(useAfterClose.isFail && sizeAfterClose.isFail)
    //     }
    // }

    // "concurrency" - {
    //     "handles concurrent object requests" in runNotJS {
    //         for
    //             counter <- AtomicInt.init(0)
    //             pool    <- Pool.init(2)(counter.incrementAndGet)
    //             results <- Async.parallelUnbounded(List.fill(10)(pool.use(identity)))
    //             count   <- counter.get
    //             size    <- pool.size
    //         yield
    //             assert(count == 2)
    //             assert(size == 2)
    //             assert(results.forall(v => v == 1 || v == 2))
    //     }

    //     "handles concurrent close" in runNotJS {
    //         for
    //             pool         <- Pool.init[Int, IO](2)(IO(1))
    //             _            <- pool.use(identity)
    //             closeFibers  <- Kyo.fill(5)(Async.run(pool.close))
    //             closeResults <- Kyo.foreach(closeFibers)(_.get)
    //         yield
    //             assert(closeResults.count(_.isDefined) == 1)
    //             assert(closeResults.exists(_ == Maybe(Seq(1))))
    //     }
    // }

    // // "effects" - {
    // //     "propagates creation effects" in run {
    // //         val v = Random.nextInt.map(_ => 42)
    // //         for
    // //             pool   <- Pool.init(1)(v)
    // //             result <- pool.use(identity)
    // //         yield assert(result == 42)
    // //         end for
    // //     }

    // //     "propagates use effects" in run {
    // //         val counter = AtomicInt.init(0)
    // //         for
    // //             pool  <- counter.map(c => Pool.init(1)(IO(1)))
    // //             _     <- pool.use(i => counter.incrementAndGet.map(_ => i))
    // //             count <- counter.get
    // //         yield assert(count == 1)
    // //         end for
    // //     }
    // // }
end PoolTest
