package kyoTest

import kyo.clocks._
import kyo._
import kyo.envs._
import kyo.ios._

import java.time.Instant
import java.time.ZoneId

class clocksTest extends KyoTest {

  object testClock extends Clock {

    var nows = List.empty[Instant]

    def now: Instant < IOs =
      IOs {
        val v = nows.head
        nows = nows.tail
        v
      }
  }

  "now" in run {
    val instant = Instant.now()
    testClock.nows = List(instant)
    val io = Clocks.let(testClock)(Clocks.now)
    assert(IOs.run(io) == instant)
  }
}
