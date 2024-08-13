package kyo

import language.higherKinds
import scala.language.experimental.macros

object TestSupport:
    transparent inline def runLiftTest[A, B](inline expected: A)(inline body: B) =
        val actual: B = IO.run(defer(body).asInstanceOf[B < IO]).eval
        if !expected.equals(actual) then
            throw new AssertionError("Expected " + expected + " but got " + actual)
    end runLiftTest
end TestSupport
