package kyo

class SignalTest extends Test:

    "Signal" - {
        "init" in run {
            for
                signal <- Signal.init[Int]
            yield assert(signal != null)
        }

        "send and receive" in run {
            for
                signal <- Signal.init[Int]
                p      <- Promise.init[Nothing, Int]
                _      <- signal.receive(v => p.completeUnit(Result.success(v)))
                _      <- signal.send(42)
                result <- p.get
            yield assert(result == 42)
        }

        "multiple receivers" in run {
            for
                signal  <- Signal.init[Int]
                p1      <- Promise.init[Nothing, Int]
                p2      <- Promise.init[Nothing, Int]
                _       <- signal.receive(v => p1.completeUnit(Result.success(v)))
                _       <- signal.receive(v => p2.completeUnit(Result.success(v)))
                _       <- signal.send(42)
                result1 <- p1.get
                result2 <- p2.get
            yield assert(result1 == 42 && result2 == 42)
        }

        "map" in run {
            for
                signal       <- Signal.init[Int]
                mappedSignal <- signal.map(v => v * 2)
                p            <- Promise.init[Nothing, Int]
                _            <- mappedSignal.receive(v => p.completeUnit(Result.success(v)))
                _            <- signal.send(21)
                result       <- p.get
            yield assert(result == 42)
        }

        "flatMap" in run {
            for
                signal1          <- Signal.init[Int]
                signal2          <- IO(Signal.init[String])
                flatMappedSignal <- signal1.flatMap(v => signal2.map(s => s"$v-$s"))
                p                <- Promise.init[Nothing, String]
                _                <- flatMappedSignal.receive(v => p.completeUnit(Result.success(v)))
                _                <- signal1.send(42)
                _                <- signal2.send("test")
                result           <- p.get
            yield assert(result == "42-test")
        }

        "filter" in run {
            for
                signal         <- Signal.init[Int]
                filteredSignal <- signal.filter(_ % 2 == 0)
                p              <- Promise.init[Nothing, Int]
                _              <- filteredSignal.receive(v => p.completeUnit(Result.success(v)))
                _              <- signal.send(41)
                _              <- signal.send(42)
                result         <- p.get
            yield assert(result == 42)
        }

        "receiveWeak" in run {
            for
                signal <- Signal.init[Int]
                p      <- Promise.init[Nothing, Int]
                _      <- signal.receiveWeak(v => p.completeUnit(Result.success(v)))
                _      <- signal.send(42)
                result <- p.get
            yield assert(result == 42)
        }
    }

end SignalTest
