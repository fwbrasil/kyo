package kyo2

import kyo2.Result
import kyo2.Result.*
import kyo2.Tagged.*

class ResultTest extends Test:

    val ex = new Exception

    val try2: Result[String, Result[String, Int]] = Success(Failure("error"))

    "should match Success containing Failure" in {
        val result = try2 match
            case Success(Failure(e)) => e
            case _                   => ""
        assert(result == "error")
    }

    "isSuccess" - {
        "returns true for Success" in {
            assert(Result.success(1).isSuccess)
        }
        "returns false for Failure" in {
            assert(!Result.failure(ex).isSuccess)
        }
        "returns false for Panic" in {
            assert(!Result.panic(ex).isSuccess)
        }
    }

    "isFailure" - {
        "returns false for Success" in {
            assert(!Result.success(1).isFailure)
        }
        "returns true for Failure" in {
            assert(Result.failure(ex).isFailure)
        }
        "returns false for Panic" in {
            assert(!Result.panic(ex).isFailure)
        }
    }

    "isPanic" - {
        "returns false for Success" in {
            assert(!Result.success(1).isPanic)
        }
        "returns false for Failure" in {
            assert(!Result.failure(ex).isPanic)
        }
        "returns true for Panic" in {
            assert(Result.panic(ex).isPanic)
        }
    }

    "get" - {
        "returns the value for Success" in {
            assert(Result.success(1).get == 1)
        }
        "can't be called for Failure" in {
            assertDoesNotCompile("Result.failure(ex).get")
        }
        "throws an exception for Panic" in {
            assertThrows[Exception](Result.panic(ex).get)
        }
    }

    "getOrElse" - {
        "returns the value for Success" in {
            assert(Result.success(1).getOrElse(0) == 1)
        }
        "returns the default value for Failure" in {
            assert(Result.failure(ex).getOrElse(0) == 0)
        }
        "returns the default value for Panic" in {
            assert(Result.panic(ex).getOrElse(0) == 0)
        }
        "inference" in {
            val r: List[String] = Result(List.empty[String]).getOrElse(List.empty)
            assert(r == List.empty)
        }
    }

    "orElse" - {
        "returns itself for Success" in {
            assert(Result.success(1).orElse(Result.success(2)) == Success(1))
        }
        "returns the alternative for Failure" in {
            assert(Result.failure(ex).orElse(Success(1)) == Success(1))
        }
        "returns the alternative for Panic" in {
            assert(Result.panic(ex).orElse(Success(1)) == Success(1))
        }
    }

    "flatMap" - {
        "applies the function for Success" in {
            assert(Result.success(1).flatMap(x => Result.success(x + 1)) == Success(2))
        }
        "does not apply the function for Failure" in {
            assert(Result.failure[String, Int]("error").flatMap(x => Success(x + 1)) == Failure("error"))
        }
        "does not apply the function for Panic" in {
            assert(Result.panic[String, Int](ex).flatMap(x => Success(x + 1)) == Panic(ex))
        }
    }

    "map" - {
        "applies the function for Success" in {
            assert(Result.success(1).map(_ + 1) == Success(2))
        }
        "does not apply the function for Failure" in {
            assert(Result.failure[String, Int]("error").map(_ + 1) == Failure("error"))
        }
        "does not apply the function for Panic" in {
            assert(Result.panic[String, Int](ex).map(_ + 1) == Panic(ex))
        }
    }

    "fold" - {
        "applies the success function for Success" in {
            assert(Result.success(1).fold(_ => 0)(_ => 0)(x => x + 1) == 2)
        }
        "applies the failure function for Failure" in {
            assert(Result.failure[String, Int]("error").fold(_ => 0)(_ => 1)(x => x) == 0)
        }
        "applies the panic function for Panic" in {
            assert(Result.panic[String, Int](ex).fold(_ => 0)(_ => 1)(x => x) == 1)
        }
    }

    "filter" - {
        "adds NoSuchElementException" in {
            val x = Result.success(2).filter(_ % 2 == 0)
            assertCompiles("val _: Result[NoSuchElementException, Int] = x")
        }
        "returns itself if the predicate holds for Success" in {
            assert(Result.success(2).filter(_ % 2 == 0) == Success(2))
        }
        "returns Panic if the predicate doesn't hold for Success" in {
            assert(Result.success(1).filter(_ % 2 == 0).isPanic)
        }
        "returns itself for Failure" in {
            assert(Result.failure[String, Int]("error").filter(_ => true) == Failure("error"))
        }
    }

    "recover" - {
        "returns itself for Success" in {
            assert(Result.success(1).recover { case _ => 0 } == Success(1))
        }
        "returns Success with the mapped value if the partial function is defined for Failure" in {
            assert(Result.failure("error").recover { case _ => 0 } == Success(0))
        }
        "returns itself if the partial function is not defined for Failure" in {
            assert(Result.failure("error").recover { case _ if false => 0 } == Failure("error"))
        }
    }

    "recoverWith" - {
        "returns itself for Success" in {
            assert(Result.success(1).recoverWith { case _ => Success(0) } == Success(1))
        }
        "returns the mapped Result if the partial function is defined for Failure" in {
            assert(Failure("error").recoverWith { case _ => Success(0) } == Success(0))
        }
        "returns itself if the partial function is not defined for Failure" in {
            val error = Failure("error")
            assert(Failure(error).recoverWith { case _ if false => Success(0) } == Failure(error))
        }
    }

    "toEither" - {
        "returns Right with the value for Success" in {
            assert(Result.success(1).toEither == Right(1))
        }
        "returns Left with the error for Failure" in {
            val error = "error"
            assert(Failure(error).toEither == Left(error))
        }
    }

    "deeply nested Result" - {
        val nestedResult = Success(Success(Success(Success(1))))

        "get should return the deeply nested value" in {
            assert(nestedResult.get.get.get.get == 1)
        }

        "map should apply the function to the deeply nested Result" in {
            assert(nestedResult.map(_.map(_.map(_.map(_ + 1)))) == Success(Success(Success(Success(2)))))
        }

        "flatMap should apply the function and flatten the result" in {
            assert(nestedResult.flatMap(x => x.flatMap(y => y.flatMap(z => z.map(_ + 1)))) == Success(2))
        }

        "isSuccess should return true for deeply nested Success" in {
            assert(nestedResult.isSuccess)
        }

        "isFailure should return false for deeply nested Success" in {
            assert(!nestedResult.isFailure)
        }
    }

    "toTry" - {
        "Success to Try" in {
            val success: Result[Nothing, Int] = Success(42)
            val tryResult                     = success.toTry
            assert(tryResult.isSuccess)
            assert(tryResult.get == 42)
        }

        "Failure to Try" in {
            val failure: Result[String, Int] = Failure("Something went wrong")
            val tryResult                    = failure.toTry
            assert(tryResult.isFailure)
            assert(tryResult.failed.get.isInstanceOf[NoSuchElementException])
        }

        "Panic to Try" in {
            val panic: Result[String, Int] = Result.panic(new Exception("Panic"))
            val tryResult                  = panic.toTry
            assert(tryResult.isFailure)
            assert(tryResult.failed.get.isInstanceOf[Exception])
            assert(tryResult.failed.get.getMessage == "Panic")
        }
    }

    "null values" - {
        "Success with null value" in {
            val result: Result[Nothing, String] = Success(null)
            assert(result.isSuccess)
            assert(result.get == null)
        }

        "Success with null value flatMap" in {
            val result: Result[Nothing, String] = Success(null)
            val flatMapped                      = result.flatMap(str => Success(s"mapped: $str"))
            assert(flatMapped == Success("mapped: null"))
        }

        "Failure with null error" taggedAs jvmOnly in {
            val result: Result[String, Int] = Failure(null)
            assert(result == Failure(null))
        }

        "Failure with null exception flatMap" taggedAs jvmOnly in {
            val result: Result[String, Int] = Failure(null)
            val flatMapped                  = result.flatMap(num => Success(num + 1))
            assert(flatMapped == Failure(null))
        }

        "Failure with null exception map" taggedAs jvmOnly in {
            val result: Result[String, Int] = Failure(null)
            val mapped                      = result.map(num => num + 1)
            assert(mapped == Failure(null))
        }
    }

    "pattern matching" - {
        "deep matching" - {
            val nestedResult = Success(Success(Success(Success(1))))

            "should match deeply nested Success and extract inner value" in {
                val result = nestedResult match
                    case Success(Success(Success(Success(x)))) => x
                assert(result == 1)
            }

            "should match partially and extract nested Result" in {
                val result = nestedResult match
                    case Success(Success(x)) => x
                assert(result == Success(Success(1)))
            }
        }

        "matching with guards" - {
            "should match Success with a guard" in {
                val tryy: Result[Nothing, Int] = Success(2)
                val result = tryy match
                    case Success(x) if x > 1 => "greater than 1"
                    case Success(_)          => "less than or equal to 1"
                    case Failure(_)          => "failure"
                assert(result == "greater than 1")
            }

            "should match Failure with a guard" in {
                val tryy: Result[String, Int] = Failure("error")
                val result = tryy match
                    case Failure(e) if e.length > 5 => "long error"
                    case Failure(_)                 => "short error"
                    case Success(_)                 => "success"
                assert(result == "short error")
            }
        }
    }

    "inference" - {
        "flatMap" in {
            val result: Result[String, Int]    = Result.success(5)
            val mapped: Result[String, String] = result.flatMap(x => Result.success(x.toString))
            assert(mapped == Success("5"))
        }

        "flatMap with different error types" in {
            val r1: Result[String, Int]              = Result.success(5)
            val r2: Result[Int, String]              = Result.success("hello")
            val nested: Result[String | Int, String] = r1.flatMap(x => r2.map(y => s"$x $y"))
            assert(nested == Success("5 hello"))
        }

        "for-comprehension with multiple flatMaps" in {
            def divideIfEven(x: Int): Result[String, Int] =
                if x % 2 == 0 then Result.success(10 / x) else Result.failure("Odd number")

            val complex: Result[String, String] =
                for
                    a <- Result.success(4)
                    b <- divideIfEven(a)
                    c <- Result.success(b * 2)
                yield c.toString

            assert(complex == Success("4"))
        }
    }

    "edge cases" - {

        "should not handle exceptions in ifFailure" in {
            val tryy      = Success(1)
            val exception = new RuntimeException("exception")
            val result =
                try
                    tryy.fold(_ => throw exception)(_ => throw exception)(_ => throw exception)
                    "no exception"
                catch
                    case e: RuntimeException => "caught exception"
            assert(result == "caught exception")
        }

        "should not handle exceptions in ifPanic" in {
            val tryy      = Result.panic(ex)
            val exception = new RuntimeException("exception")
            val result =
                try
                    tryy.fold(_ => throw exception)(_ => throw exception)(_ => throw exception)
                    "no exception"
                catch
                    case e: RuntimeException => "caught exception"
            assert(result == "caught exception")
        }

        "should handle exceptions in ifSuccess" in {
            val tryy      = Success(1)
            val exception = new RuntimeException("exception")
            val result =
                try
                    tryy.fold(_ => 0)(_ => 0)(_ => throw exception)
                    "no exception"
                catch
                    case e: RuntimeException => "caught exception"
            assert(result == "no exception")
        }

        "should handle exceptions during map" in {
            val tryy      = Success(1)
            val exception = new RuntimeException("exception")
            val result =
                try
                    tryy.map(_ => throw exception)
                    "no exception"
                catch
                    case e: RuntimeException => "caught exception"
            assert(result == "no exception")
        }

        "should handle exceptions during flatMap" in {
            val tryy      = Success(1)
            val exception = new RuntimeException("exception")
            val result =
                try
                    tryy.flatMap(_ => throw exception)
                    "no exception"
                catch
                    case e: RuntimeException => "caught exception"
            assert(result == "no exception")
        }

        "nested Success containing Failure" in {
            val nested: Result[String, Result[Int, String]] = Result.success(Result.failure(42))
            val flattened                                   = nested.flatten

            assert(flattened == Failure(42))
        }

        "recover with a partial function" in {
            val result: Result[String, Int] = Result.failure("error")
            val recovered = result.recover {
                case Failure("error") => 0
                case _                => -1
            }

            assert(recovered == Success(0))
        }

        "empty Success" in {
            val empty: Result[Nothing, Unit] = Result.success(())
            assert(empty == Success(()))
        }

        "Panic distinct from Failure" in {
            val exception = new RuntimeException("Unexpected error")
            val panic     = Result.panic(exception)
            assert(!panic.isFailure)
            assert(panic match
                case Panic(_) => true
                case _        => false
            )
        }

        "deeply nested Success/Failure" in {
            val deeplyNested = Success(Success(Success(Failure("deep error"))))
            assert(deeplyNested.flatten == Success(Success(Failure("deep error"))))
            assert(deeplyNested.flatten.flatten == Success(Failure("deep error")))
            assert(deeplyNested.flatten.flatten.flatten == Failure("deep error"))
        }

        "Panic propagation through flatMap" in {
            val panic  = Result.panic(new RuntimeException("Unexpected"))
            val result = panic.flatMap(_ => Success(1)).flatMap(_ => Failure("won't happen"))
            assert(result == panic)
        }

        "Error type widening" in {
            val r1: Result[String, Int] = Failure("error1")
            val r2: Result[Int, String] = Failure(42)
            val combined                = r1.orElse(r2)
            assert(combined.isFailure)
            assert(combined match
                case Failure(_: String | _: Int) => true
                case _                           => false
            )
        }

        "nested flatMap with type changes" in {
            def f(i: Int): Result[String, Int] =
                if i > 0 then Success(i) else Failure("non-positive")
            def g(d: Int): Result[Int, String] =
                if d < 10 then Success(d.toString) else Failure(d.toInt)

            val result = Success(5).flatMap(f).flatMap(g)
            assert(result == Success("5"))

            val result2 = Success(-1).flatMap(f).flatMap(g)
            assert(result2 == Failure("non-positive"))

            val result3 = Success(20).flatMap(f).flatMap(g)
            assert(result3 == Failure(20))
        }
    }

end ResultTest
