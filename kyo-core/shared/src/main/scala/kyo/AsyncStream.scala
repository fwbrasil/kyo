package kyo

export AsyncStream.*
import kyo.Emit.Ack
import kyo.kernel.Boundary
import kyo.kernel.Reducible

object AsyncStream:

    extension (self: Stream.type)
        def init[V](channel: Channel[Chunk[V]])(using tag: Tag[Emit[Chunk[V]]], frame: Frame): Stream[V, Async] =
            Stream {
                Abort.recover[Closed](_ => Ack.Stop) {
                    Loop(Ack.Continue(): Ack) {
                        case Ack.Stop => Loop.done(Ack.Stop)
                        case _        => channel.take.map(v => Emit.andMap(v)(Loop.continue))
                    }
                }
            }

    extension [V, E, Ctx](self: Stream[V, Abort[E] & Async & Ctx])

        def buffer(capacity: Int)(
            using
            reduce: Reducible[Abort[E]],
            boundary: Boundary[Ctx, Abort[E] & IO],
            tag: Tag[Emit[Chunk[V]]],
            frame: Frame
        ): Stream[V, reduce.SReduced & Async & Ctx] =
            Stream(runChannel(capacity).map(ch => Stream.init(ch).emit))

        def runChannel(capacity: Int)(
            using
            reduce: Reducible[Abort[E]],
            boundary: Boundary[Ctx, Abort[E] & IO],
            tag: Tag[Emit[Chunk[V]]],
            frame: Frame
        ): Channel[Chunk[V]] < (reduce.SReduced & IO & Ctx) =
            Channel.init[Chunk[V]](capacity).map { channel =>
                Async.run {
                    IO.ensure(channel.close.unit) {
                        Emit.runAck(self.emit) { chunk =>
                            Abort.recover[Closed](_ => Ack.Stop)(channel.put(chunk).andThen(Ack.Continue()))
                        }
                    }
                }.as(channel)
            }
    end extension
end AsyncStream
