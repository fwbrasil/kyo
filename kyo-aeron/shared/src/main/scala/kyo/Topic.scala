package kyo

import io.aeron.Aeron

opaque type Bus <: (Async & Resource & Abort[Closed]) = Env[Aeron] & Async & Resource & Abort[Closed]

object Bus:
    // introduce more convenient APIs to intialize Aeron
    def run[A: Flat, S](aeron: Aeron)(v: A < (Bus & S))(using Frame): A < (Async & Abort[Closed] & S) =
        Env.run(aeron)(Resource.run(v))
end Bus

class Topic[A: Tag as tag] private (channel: String):
    def publisher: Topic.Publisher[A] < Bus =
        Env.use[Aeron] { aeron =>
            val pub    = aeron.addPublication(channel, tag.hashCode())
            val buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024 * 1024))
        }
        ???
    end publisher
    def stream: Stream[A, Bus] = ???
end Topic

object Topic:

    def init[A: Tag](channel: String): Topic[A] = Topic(channel)

    abstract class Publisher[A]:
        def publish(msg: A): Unit < Bus

end Topic
