package kyo.internal

import kyo.Result
import scala.compiletime.testing.typeCheckErrors
import scala.util.Try

private[kyo] trait BaseKyoDataTest:

    type Assertion

    inline def assertionSuccess: Assertion
    inline def assertionFailure(msg: String): Assertion

    given [A]: CanEqual[Try[A], Try[A]]                = CanEqual.derived
    given [A, B]: CanEqual[Either[A, B], Either[A, B]] = CanEqual.derived
    given CanEqual[Throwable, Throwable]               = CanEqual.derived

    inline def typeCheck(inline code: String): Result[String, Unit] =
        try
            val errors = typeCheckErrors(code)
            if errors.isEmpty then Result.unit else Result.fail(errors.iterator.map(_.message).mkString("\n"))
        catch
            case cause: Throwable =>
                Result.panic(new RuntimeException("Compilation failed", cause))
    end typeCheck

    inline def typeCheckProbe(inline code: String): Assertion =
        typeCheck(code) match
            case Result.Failure(typeCheckErrors) => assertionFailure(s"Type check probe errors: \n$typeCheckErrors")
            case Result.Panic(exception)         => assertionFailure(exception.getMessage)
            case Result.Success(_)               => assertionFailure("Code type-checked successfully, expected a failure")

    inline def typeCheckFailure(inline code: String)(inline error: String): Assertion =
        typeCheck(code) match
            case Result.Failure(typeCheckErrors) =>
                if typeCheckErrors.contains(error) && !error.isEmpty() then assertionSuccess
                else assertionFailure(s"Type check error '$error' not found: $typeCheckErrors")
            case Result.Panic(exception) => assertionFailure(exception.getMessage)
            case Result.Success(_)       => assertionFailure("Code type-checked successfully, expected a failure")

end BaseKyoDataTest
