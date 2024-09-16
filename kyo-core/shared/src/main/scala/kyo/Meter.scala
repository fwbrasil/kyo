package kyo

abstract class Meter:
    self =>

    def available(using Frame): Int < IO

    def isAvailable(using Frame): Boolean < IO =
        available.map(_ > 0)

    def run[A, S](v: => A < S)(using Frame): A < (S & Async)

    def tryRun[A, S](v: => A < S)(using Frame): Maybe[A] < (IO & S)

    def close(using Frame): Boolean < IO
end Meter

object Meter:

    case object Noop extends Meter:
        def available(using Frame)                 = Int.MaxValue
        def run[A, S](v: => A < S)(using Frame)    = v
        def tryRun[A, S](v: => A < S)(using Frame) = v.map(Maybe(_))
        def close(using Frame)                     = false
    end Noop

    def initMutex(using Frame): Meter < IO =
        initSemaphore(1)

    def initSemaphore(concurrency: Int)(using Frame): Meter < IO =
        Channel.init[Unit](concurrency).map { chan =>
            offer(concurrency, chan, ()).map { _ =>
                new Meter:
                    def available(using Frame) = chan.size
                    def release(using Frame)   = chan.offerUnit(())

                    def run[A, S](v: => A < S)(using Frame) =
                        IO.ensure(release) {
                            chan.take.andThen(v)
                        }

                    def tryRun[A, S](v: => A < S)(using Frame) =
                        IO {
                            chan.unsafePoll match
                                case Maybe.Empty => Maybe.empty
                                case _ =>
                                    IO.ensure(release) {
                                        v.map(Maybe(_))
                                    }
                        }

                    def close(using Frame) =
                        chan.close.map(_.isDefined)
            }
        }

    def initRateLimiter(rate: Int, period: Duration)(using Frame): Meter < IO =
        Channel.init[Unit](rate).map { chan =>
            Timer.scheduleAtFixedRate(period)(offer(rate, chan, ())).map { _ =>
                new Meter:

                    def available(using Frame)              = chan.size
                    def run[A, S](v: => A < S)(using Frame) = chan.take.map(_ => v)

                    def tryRun[A, S](v: => A < S)(using Frame) =
                        chan.poll.map {
                            case Maybe.Empty =>
                                Maybe.empty
                            case _ =>
                                v.map(Maybe(_))
                        }

                    def close(using Frame) =
                        chan.close.map(_.isDefined)
            }
        }

    def pipeline[S1, S2](m1: Meter < S1, m2: Meter < S2)(using Frame): Meter < (IO & S1 & S2) =
        pipeline[S1 & S2](List(m1, m2))

    def pipeline[S1, S2, S3](
        m1: Meter < S1,
        m2: Meter < S2,
        m3: Meter < S3
    )(using Frame): Meter < (IO & S1 & S2 & S3) =
        pipeline[S1 & S2 & S3](List(m1, m2, m3))

    def pipeline[S1, S2, S3, S4](
        m1: Meter < S1,
        m2: Meter < S2,
        m3: Meter < S3,
        m4: Meter < S4
    )(using Frame): Meter < (IO & S1 & S2 & S3 & S4) =
        pipeline[S1 & S2 & S3 & S4](List(m1, m2, m3, m4))

    def pipeline[S](meters: Seq[Meter < (IO & S)])(using Frame): Meter < (IO & S) =
        Kyo.collect(meters).map { seq =>
            val meters = seq.toIndexedSeq
            new Meter:

                def available(using Frame) =
                    Loop.indexed(0) { (idx, acc) =>
                        if idx == meters.length then Loop.done(acc)
                        else meters(idx).available.map(v => Loop.continue(acc + v))
                    }

                def run[A, S](v: => A < S)(using Frame) =
                    def loop(idx: Int = 0): A < (S & Async) =
                        if idx == meters.length then v
                        else meters(idx).run(loop(idx + 1))
                    loop()
                end run

                def tryRun[A, S](v: => A < S)(using Frame) =
                    def loop(idx: Int = 0): Maybe[A] < (S & IO) =
                        if idx == meters.length then v.map(Maybe(_))
                        else
                            meters(idx).tryRun(loop(idx + 1)).map {
                                case Maybe.Empty => Maybe.empty
                                case r           => r.flatten
                            }
                    loop()
                end tryRun

                def close(using Frame): Boolean < IO =
                    Kyo.foreach(meters)(_.close).map(_.exists(identity))
            end new
        }

    private def offer[A](n: Int, chan: Channel[A], v: A)(using Frame): Unit < IO =
        Loop.indexed { idx =>
            if idx == n then Loop.done
            else
                chan.offer(v).map {
                    case true  => Loop.continue
                    case false => Loop.done
                }
        }
end Meter
