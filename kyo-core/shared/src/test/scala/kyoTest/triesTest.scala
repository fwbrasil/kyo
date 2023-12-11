package kyoTest

import kyo._
import kyo.options._
import kyo.tries._
import org.scalatest.Args
import org.scalatest.Status

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import kyo.aborts.Aborts

class triesTest extends KyoTest {

  val e = new Exception

  ".catching" - {
    "failure" in {
      assert(
          Tries.run(Tries.catching((throw e): Int)) ==
            Failure(e)
      )
    }
    "success" in {
      assert(
          Tries.run(Tries.catching(1)) ==
            Success(1)
      )
    }
  }

  "run" - {
    "failure" in {
      assert(
          Tries.run(Tries.catching((throw e): Int)) ==
            Failure(e)
      )
    }
    "success" in {
      assert(
          Tries.run(Tries.catching(1)) ==
            Success(1)
      )
    }
  }

  "get" - {
    "failure" in {
      assert(
          Tries.run(Tries.get(Failure[Int](e))) ==
            Failure(e)
      )
    }
    "success" in {
      assert(
          Tries.run(Tries.get(Success(1))) ==
            Success(1)
      )
    }
  }

  "fail" in {
    assert(
        Tries.run(Tries.fail[Int](e)) ==
          Failure(e)
    )
  }

  "pure" - {
    "handle" in {
      assert(
          Tries.run(1: Int < Tries) ==
            Try(1)
      )
    }
    "handle + transform" in {
      assert(
          Tries.run((1: Int < Tries).map(_ + 1)) ==
            Try(2)
      )
    }
    "handle + effectful transform" in {
      assert(
          Tries.run((1: Int < Tries).map(i => Tries.get(Try(i + 1)))) ==
            Try(2)
      )
    }
    "handle + transform + effectful transform" in {
      assert(
          Tries.run((1: Int < Tries).map(_ + 1).map(i => Tries.get(Try(i + 1)))) ==
            Try(3)
      )
    }
    "handle + transform + failed effectful transform" in {
      val e = new Exception
      assert(
          Tries.run((1: Int < Tries).map(_ + 1).map(i => Tries.get(Try[Int](throw e)))) ==
            Failure(e)
      )
    }
  }

  "effectful" - {
    "handle" in {
      assert(
          Tries.run(Tries.get(Try(1))) ==
            Try(1)
      )
    }
    "handle + transform" in {
      assert(
          Tries.run(Tries.get(Try(1)).map(_ + 1)) ==
            Try(2)
      )
    }
    "handle + effectful transform" in {
      assert(
          Tries.run(Tries.get(Try(1)).map(i => Tries.get(Try(i + 1)))) ==
            Try(2)
      )
    }
    "handle + transform + effectful transform" in {
      assert(
          Tries.run((Tries.get(Try(1))).map(_ + 1).map(i => Tries.get(Try(i + 1)))) ==
            Try(3)
      )
    }
    "handle + failed transform" in {
      assert(
          Tries.run((Tries.get(Try(1))).map(_ => (throw e): Int)) ==
            Failure(e)
      )
    }
    "handle + transform + effectful transform + failed transform" in {
      assert(
          Tries.run((Tries.get(Try(1))).map(_ + 1).map(i => Tries.get(Try(i + 1))).map(_ =>
            (throw e): Int
          )) ==
            Failure(e)
      )
    }
    "handle + transform + failed effectful transform" in {
      assert(
          Tries.run((Tries.get(Try(1))).map(_ + 1).map(i => Tries.get(Try((throw e): Int)))) ==
            Failure(e)
      )
    }
    "nested effect + failure" in {

      assert(
          Options.run(
              Tries.run[Int, Options](
                  Tries.get(Try(Option(1))).map(opt =>
                    Options.get(opt: Option[Int] < Tries).map(_ => (throw e): Int)
                  )
              )
          ) ==
            Some(Failure(e))
      )
    }
  }
}
