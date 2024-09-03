package kyo

import System.Parser
import java.lang as j

class SystemTest extends Test:

    "env" - {
        "existing variable" in runJVM {
            for
                path <- System.env[String]("PATH")
            yield assert(path.isDefined && path.get.nonEmpty)
        }

        "non-existing variable" in run {
            for
                result <- System.env[String]("NON_EXISTING_VARIABLE")
            yield assert(result.isEmpty)
        }

        "with default value" in run {
            for
                result <- System.env[String]("NON_EXISTING_VARIABLE", "default")
            yield assert(result == "default")
        }
    }

    "property" - {
        "existing property" in run {
            val key = "java.version"
            for
                result <- System.property[String](key)
                actual = j.System.getProperty(key)
            yield assert(result.contains(actual))
            end for
        }

        "non-existing property" in run {
            for
                result <- System.property[String]("non.existing.property")
            yield assert(result.isEmpty)
        }

        "with default value" in run {
            for
                result <- System.property[String]("non.existing.property", "default")
            yield assert(result == "default")
        }

        "set and get property" in run {
            val key   = "test.property"
            val value = "test.value"
            j.System.setProperty(key, value)
            for
                result <- System.property[String](key)
            yield
                j.System.clearProperty(key)
                assert(result.contains(value))
            end for
        }
    }

    "lineSeparator" in run {
        for
            separator <- System.lineSeparator
            expected = j.System.lineSeparator()
        yield assert(separator == expected)
    }

    "userName" in run {
        for
            name <- System.userName
            expected = j.System.getProperty("user.name")
        yield assert(name == expected)
    }

    "userHome" in runJVM {
        for
            home <- System.userHome
            expected = j.System.getProperty("user.home")
        yield assert(home == expected)
    }

    "custom System implementation" in run {
        val customSystem = new System:
            def env[E, A](name: String)(using Parser[E, A], Frame): Maybe[A] < (Abort[E] & IO) =
                IO(Maybe("custom_env").asInstanceOf[Maybe[A]])
            def property[E, A](name: String)(using Parser[E, A], Frame): Maybe[A] < (Abort[E] & IO) =
                IO(Maybe("custom_property").asInstanceOf[Maybe[A]])
            def lineSeparator(using Frame): String < IO = IO("custom_separator")
            def userName(using Frame): String < IO      = IO("custom_user")
            def userHome(using Frame): String < IO      = IO("custom_home")

        for
            env       <- System.let(customSystem)(System.env[String]("ANY"))
            prop      <- System.let(customSystem)(System.property[String]("ANY"))
            separator <- System.let(customSystem)(System.lineSeparator)
            user      <- System.let(customSystem)(System.userName)
            home      <- System.let(customSystem)(System.userHome)
        yield
            assert(env == Maybe("custom_env"))
            assert(prop == Maybe("custom_property"))
            assert(separator == "custom_separator")
            assert(user == "custom_user")
            assert(home == "custom_home")
        end for
    }

    "Parser" - {
        val TEST_PROP = "TEST_PROP"

        "String" - {
            "valid string" in run {
                j.System.setProperty(TEST_PROP, "test_value")
                for
                    result <- System.property[String](TEST_PROP)
                yield assert(result == Maybe("test_value"))
            }
            "empty string" in run {
                j.System.setProperty(TEST_PROP, "")
                for
                    result <- System.property[String](TEST_PROP)
                yield assert(result == Maybe(""))
            }
        }

        "Int" - {
            "valid int" in run {
                j.System.setProperty(TEST_PROP, "42")
                for
                    result <- System.property[Int](TEST_PROP)
                yield assert(result == Maybe(42))
            }
            "negative int" in run {
                j.System.setProperty(TEST_PROP, "-10")
                for
                    result <- System.property[Int](TEST_PROP)
                yield assert(result == Maybe(-10))
            }
            "zero" in run {
                j.System.setProperty(TEST_PROP, "0")
                for
                    result <- System.property[Int](TEST_PROP)
                yield assert(result == Maybe(0))
            }
            "invalid int" in run {
                j.System.setProperty(TEST_PROP, "not_a_number")
                for
                    result <- Abort.run(System.property[Int](TEST_PROP))
                yield assert(result.isFail)
            }
        }

        "Boolean" - {
            "true" in run {
                j.System.setProperty(TEST_PROP, "true")
                for
                    result <- System.property[Boolean](TEST_PROP)
                yield assert(result == Maybe(true))
            }
            "false" in run {
                j.System.setProperty(TEST_PROP, "false")
                for
                    result <- System.property[Boolean](TEST_PROP)
                yield assert(result == Maybe(false))
            }
            "invalid boolean" in run {
                j.System.setProperty(TEST_PROP, "not_a_boolean")
                for
                    result <- Abort.run(System.property[Boolean](TEST_PROP))
                yield assert(result.isFail)
            }
        }

        "Double" - {
            "valid double" in run {
                j.System.setProperty(TEST_PROP, "3.14")
                for
                    result <- System.property[Double](TEST_PROP)
                yield assert(result == Maybe(3.14))
            }
            "negative double" in run {
                j.System.setProperty(TEST_PROP, "-2.5")
                for
                    result <- System.property[Double](TEST_PROP)
                yield assert(result == Maybe(-2.5))
            }
            "zero double" in run {
                j.System.setProperty(TEST_PROP, "0.0")
                for
                    result <- System.property[Double](TEST_PROP)
                yield assert(result == Maybe(0.0))
            }
            "scientific notation" in run {
                j.System.setProperty(TEST_PROP, "1.23e-4")
                for
                    result <- System.property[Double](TEST_PROP)
                yield assert(result == Maybe(1.23e-4))
            }
            "invalid double" in run {
                j.System.setProperty(TEST_PROP, "not_a_double")
                for
                    result <- Abort.run(System.property[Double](TEST_PROP))
                yield assert(result.isFail)
            }
        }

        "Long" - {
            "valid long" in run {
                j.System.setProperty(TEST_PROP, "9223372036854775807")
                for
                    result <- System.property[Long](TEST_PROP)
                yield assert(result == Maybe(Long.MaxValue))
            }
            "negative long" in run {
                j.System.setProperty(TEST_PROP, "-9223372036854775808")
                for
                    result <- System.property[Long](TEST_PROP)
                yield assert(result == Maybe(Long.MinValue))
            }
            "invalid long" in run {
                j.System.setProperty(TEST_PROP, "not_a_long")
                for
                    result <- Abort.run(System.property[Long](TEST_PROP))
                yield assert(result.isFail)
            }
        }

        "Char" - {
            "valid char" in run {
                j.System.setProperty(TEST_PROP, "a")
                for
                    result <- System.property[Char](TEST_PROP)
                yield assert(result == Maybe('a'))
            }
            "invalid char (empty string)" in run {
                j.System.setProperty(TEST_PROP, "")
                for
                    result <- Abort.run(System.property[Char](TEST_PROP))
                yield assert(result.isFail)
            }
            "invalid char (multiple characters)" in run {
                j.System.setProperty(TEST_PROP, "abc")
                for
                    result <- Abort.run(System.property[Char](TEST_PROP))
                yield assert(result.isFail)
            }
        }

        "Duration" - {
            "valid duration" in run {
                j.System.setProperty(TEST_PROP, "10s")
                for
                    result <- System.property[Duration](TEST_PROP)
                yield assert(result == Maybe(10.seconds))
            }
            "invalid duration" in run {
                j.System.setProperty(TEST_PROP, "not_a_duration")
                for
                    result <- Abort.run(System.property[Duration](TEST_PROP))
                yield assert(result.isFail)
            }
        }

        "UUID" - {
            "valid UUID" in run {
                val validUUID = "550e8400-e29b-41d4-a716-446655440000"
                j.System.setProperty(TEST_PROP, validUUID)
                for
                    result <- System.property[java.util.UUID](TEST_PROP)
                yield assert(result.get.equals(java.util.UUID.fromString(validUUID)))
            }
            "invalid UUID" in run {
                j.System.setProperty(TEST_PROP, "not_a_uuid")
                for
                    result <- Abort.run(System.property[java.util.UUID](TEST_PROP))
                yield assert(result.isFail)
            }
        }

        "LocalDate" - {
            "valid LocalDate" in run {
                j.System.setProperty(TEST_PROP, "2023-05-17")
                for
                    result <- System.property[java.time.LocalDate](TEST_PROP)
                yield assert(result.get.equals(java.time.LocalDate.of(2023, 5, 17)))
            }
            "invalid LocalDate" in run {
                j.System.setProperty(TEST_PROP, "not_a_date")
                for
                    result <- Abort.run(System.property[java.time.LocalDate](TEST_PROP))
                yield assert(result.isFail)
            }
        }

    }
end SystemTest
