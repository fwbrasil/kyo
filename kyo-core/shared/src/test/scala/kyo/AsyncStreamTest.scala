package kyo

class AsyncStreamTest extends Test:

    "buffer" - {
        // "empty stream" in run {
        //     val stream = Stream.empty[Int]
        //     stream.buffer(1).run.map { result =>
        //         assert(result.isEmpty)
        //     }
        // }

        // "successful buffering" in run {
        //     val chunk  = Chunk.from(1 to 10)
        //     val stream = Stream.init(chunk)
        //     stream.buffer(1).run.map { result =>
        //         assert(result == chunk)
        //     }
        // }

        // "respects buffer capacity" in run {
        //     var produced = 0
        //     var consumed = 0

        //     val stream = Stream.init(1 to 10).map { i =>
        //         produced += 1
        //         i
        //     }

        //     for
        //         buffered <- stream.buffer(2)
        //         result <- buffered.map { i =>
        //             consumed += 1
        //             Async.sleep(10.millis).as(i)
        //         }.run
        //         _ = assert(produced >= consumed) // Producer stays ahead of consumer
        //     yield assert(result == (1 to 10))
        //     end for
        // }

        // "handles errors in producer" in run {
        //     val stream = Stream.init(1 to 5).map { i =>
        //         if i == 3 then throw new RuntimeException("Test error")
        //         else i
        //     }

        //     for
        //         buffered <- stream.buffer(2)
        //         result   <- Abort.run[Throwable](buffered.run)
        //     yield assert(result.isPanic)
        //     end for
        // }

        // "handles errors in consumer" in run {
        //     val stream = Stream.init(1 to 5)

        //     for
        //         buffered <- stream.buffer(2)
        //         transformed = buffered.map { i =>
        //             if i == 3 then throw new RuntimeException("Test error")
        //             else i
        //         }
        //         result <- Abort.run[Throwable](transformed.run)
        //     yield assert(result.isPanic)
        //     end for
        // }

        // "cleans up resources on cancellation" in run {
        //     var cleaned = false
        //     val stream = Stream.init(1 to 5).map { i =>
        //         IO.ensure(cleaned = true)(i)
        //     }

        //     for
        //         fiber <- Async.run(stream.buffer(2).run)
        //         _     <- fiber.interrupt
        //         _     <- Async.sleep(10.millis) // Give time for cleanup
        //     yield assert(cleaned)
        //     end for
        // }
    }

    "runChannel" - {
        "empty stream" in run {
            val stream = Stream.empty[Int]
            for
                channel <- stream.runChannel(1)
                chunk   <- channel.take
                _       <- channel.close
            yield assert(chunk.isEmpty)
            end for
        }

        // "successful streaming" in run {
        //     val stream = Stream.init(1 to 5)
        //     for
        //         channel <- stream.runChannel(2)
        //         chunks <- Kyo.unfoldWhile(List.empty[Chunk[Int]]) { acc =>
        //             channel.take.map { chunk =>
        //                 if chunk.isEmpty then None
        //                 else Some(chunk :: acc)
        //             }
        //         }
        //         _ <- channel.close
        //     yield assert(chunks.reverse.flatMap(_.toList) == (1 to 5).toList)
        //     end for
        // }

        // "handles backpressure" in run {
        //     var produced = 0
        //     var consumed = 0

        //     val stream = Stream.init(1 to 10).map { i =>
        //         produced += 1
        //         i
        //     }

        //     for
        //         channel <- stream.runChannel(2)
        //         _ <- Kyo.unfoldWhile(()) { _ =>
        //             for
        //                 chunk <- channel.take
        //                 _     <- Async.sleep(10.millis)
        //                 _ = consumed += chunk.size
        //             yield if chunk.isEmpty then None else Some(())
        //         }
        //         _ <- channel.close
        //         _ = assert(produced >= consumed) // Producer stays ahead of consumer
        //     yield assert(consumed == 10)
        //     end for
        // }

        // "handles producer errors" in run {
        //     val stream = Stream.init(1 to 5).map { i =>
        //         if i == 3 then throw new RuntimeException("Test error")
        //         else i
        //     }

        //     for
        //         channel <- stream.runChannel(2)
        //         result  <- Abort.run[Throwable](channel.take)
        //         _       <- channel.close
        //     yield assert(result.isPanic)
        //     end for
        // }

        // "closes channel on errors" in run {
        //     var closed = false
        //     val stream = Stream.init(1 to 5).map { i =>
        //         if i == 3 then throw new RuntimeException("Test error")
        //         else i
        //     }

        //     for
        //         channel <- stream.runChannel(2)
        //         _       <- IO.ensure(closed = true)(channel.close)
        //         result  <- Abort.run[Throwable](channel.take)
        //     yield
        //         assert(result.isPanic)
        //         assert(closed)
        //     end for
        // }

        // "handles concurrent consumers" in run {
        //     val stream = Stream.init(1 to 10)

        //     for
        //         channel <- stream.runChannel(2)
        //         fiber1  <- Async.run(channel.take)
        //         fiber2  <- Async.run(channel.take)
        //         chunk1  <- fiber1.get
        //         chunk2  <- fiber2.get
        //         _       <- channel.close
        //     yield assert(chunk1.size + chunk2.size <= 10)
        //     end for
        // }
    }

end AsyncStreamTest
