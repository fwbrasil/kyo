package kyoTest

import kyo.clocks._
import kyo.concurrent.fibers._
import kyo.concurrent.timers._
import kyo.consoles._
import kyo.core._
import kyo.ios._
import kyo.randoms._
import kyo.resources._

import scala.concurrent.duration._

class KyoAppTest extends KyoTest {

  "args" in {
    var args: List[String] = Nil
    val app: kyo.KyoApp = (a: List[String]) => {
      args = a
      ()
    }
    app.main(Array("hello", "world"))
    assert(args == List("hello", "world"))
  }

  "effects" in run {
    val app: kyo.KyoApp = (a: List[String]) => {
      for {
        _ <- Timers.scheduleAtFixedRate(1.second, 1.second)(())
        _ <- Randoms.nextInt
        _ <- Consoles.println("1")
        _ <- Clocks.now
        _ <- Resources.ensure(())
        _ <- Fibers.fork(())
      } yield ()
    }
    app.main(Array())
    succeed
  }
}
