package kyo

class FilterTest extends Test:

    "Filter creation" - {
        "single field predicates" - {
            "equality" in {
                val filter = ~"name" == "John"
                assert(filter("name" ~ "John" & "age" ~ 30))
                assert(!filter("name" ~ "Jane" & "age" ~ 30))
            }

            "greater than" in {
                val filter = ~"age" > 25
                assert(filter("name" ~ "John" & "age" ~ 30))
                assert(!filter("name" ~ "John" & "age" ~ 20))
            }

            "less than" in {
                val filter = ~"age" < 40
                assert(filter("name" ~ "John" & "age" ~ 30))
                assert(!filter("name" ~ "John" & "age" ~ 50))
            }

            "greater than or equal" in {
                val filter = ~"age" >= 30
                assert(filter("name" ~ "John" & "age" ~ 30))
                assert(filter("name" ~ "John" & "age" ~ 35))
                assert(!filter("name" ~ "John" & "age" ~ 25))
            }

            "less than or equal" in {
                val filter = ~"age" <= 30
                assert(filter("name" ~ "John" & "age" ~ 30))
                assert(filter("name" ~ "John" & "age" ~ 25))
                assert(!filter("name" ~ "John" & "age" ~ 35))
            }
        }

        "string operations" - {
            "like" in {
                val filter = ~"name" like "Jo%"
                assert(filter("name" ~ "John" & "age" ~ 30))
                assert(filter("name" ~ "Josh" & "age" ~ 25))
                assert(!filter("name" ~ "Mary" & "age" ~ 28))
            }

            "like with special regex characters" in {
                val filter = ~"email" like "%.com"
                assert(filter("email" ~ "test@example.com"))
                assert(!filter("email" ~ "test@example.net"))

                val filter2 = ~"path" like "/usr/./bin"
                assert(filter2("path" ~ "/usr/./bin"))
                assert(!filter2("path" ~ "/usr/bin"))
            }

            "like with underscore wildcard" in {
                val filter = ~"code" like "A_C"
                assert(filter("code" ~ "ABC"))
                assert(filter("code" ~ "ADC"))
                assert(!filter("code" ~ "ABBC"))
            }

            "exact match" in {
                val filter = ~"name" == "John"
                assert(filter("name" ~ "John" & "age" ~ 30))
                assert(!filter("name" ~ "Jane" & "age" ~ 30))
            }
        }

        "set operations" - {
            "in" in {
                val filter = ~"age" in (25, 30, 35)
                assert(filter("name" ~ "John" & "age" ~ 30))
                assert(!filter("name" ~ "John" & "age" ~ 28))
            }

            "in with single value" in {
                val filter = ~"status" in ("active")
                assert(filter("name" ~ "John" & "status" ~ "active"))
                assert(!filter("name" ~ "John" & "status" ~ "inactive"))
            }

            "between" in {
                val filter = ~"age" between (25, 35)
                assert(filter("name" ~ "John" & "age" ~ 30))
                assert(!filter("name" ~ "John" & "age" ~ 40))
            }

            "between inclusive bounds" in {
                val filter = ~"age" between (25, 25)
                assert(filter("name" ~ "John" & "age" ~ 25))
                assert(!filter("name" ~ "John" & "age" ~ 24))
                assert(!filter("name" ~ "John" & "age" ~ 26))
            }

            "between with decimal values" in {
                val filter = ~"score" between (99.5, 100.0)
                assert(filter("name" ~ "John" & "score" ~ 99.75))
                assert(!filter("name" ~ "John" & "score" ~ 99.4))
            }
        }
    }

    "Filter composition" - {
        "AND operator" in {
            val filter = ~"name" == "John" && ~"age" > 25
            assert(filter("name" ~ "John" & "age" ~ 30))
            assert(!filter("name" ~ "John" & "age" ~ 20))
            assert(!filter("name" ~ "Jane" & "age" ~ 30))
        }

        "OR operator" in {
            val filter = ~"age" < 25 || ~"age" > 35
            assert(filter("name" ~ "John" & "age" ~ 20))
            assert(filter("name" ~ "Jane" & "age" ~ 40))
            assert(!filter("name" ~ "John" & "age" ~ 30))
        }

        "NOT operator" in {
            val filter = !(~"age" > 30)
            assert(filter("name" ~ "John" & "age" ~ 25))
            assert(!filter("name" ~ "John" & "age" ~ 35))
        }

        "complex combinations" in {
            val filter = (~"name" == "John" && ~"age" > 25) || (~"name" == "Jane" && ~"age" < 30)
            assert(filter("name" ~ "John" & "age" ~ 30))
            assert(filter("name" ~ "Jane" & "age" ~ 25))
            assert(!filter("name" ~ "John" & "age" ~ 20))
            assert(!filter("name" ~ "Jane" & "age" ~ 35))
        }
    }

    "Filter with different value types" - {
        "string values" in {
            val filter = ~"name" == "John" && ~"city" == "Paris"
            assert(filter("name" ~ "John" & "city" ~ "Paris" & "age" ~ 30))
            assert(!filter("name" ~ "John" & "city" ~ "London" & "age" ~ 30))
        }

        "numeric values" in {
            val filter = ~"age" > 25 && ~"salary" <= 100000
            assert(filter("name" ~ "John" & "age" ~ 30 & "salary" ~ 90000))
            assert(!filter("name" ~ "John" & "age" ~ 20 & "salary" ~ 110000))
        }

        "boolean values" in {
            val filter = ~"active" == true
            assert(filter("name" ~ "John" & "active" ~ true))
            assert(!filter("name" ~ "John" & "active" ~ false))
        }
    }

    "Filter operation edge cases" - {
        "empty string" in {
            val filter = ~"name" == ""
            assert(filter("name" ~ "" & "age" ~ 30))
            assert(!filter("name" ~ "John" & "age" ~ 30))
        }

        "boundary values for numbers" in {
            val filter = ~"age" > Int.MinValue && ~"age" < Int.MaxValue
            assert(filter("name" ~ "John" & "age" ~ 0))
            assert(filter("name" ~ "John" & "age" ~ -1))
            assert(filter("name" ~ "John" & "age" ~ 1))
        }
    }

    "Filter type safety" - {
        "type mismatch should not compile" in {
            assertDoesNotCompile("""
              val filter = ~"age" == "not a number"
              assert(filter("name" ~ "John" & "age" ~ 0))
            """)
        }

        "undefined field should not compile" in {
            assertDoesNotCompile("""
              val filter = ~"undefined" == "value"
              assert(filter("name" ~ "John" & "age" ~ 0))
            """)
        }
    }

    "complex type scenarios" - {
        "nested filter combinations" in {
            val ageFilter    = ~"age" between (20, 30)
            val nameFilter   = ~"name" like "J%"
            val statusFilter = ~"status" in ("active", "pending")

            val complexFilter = (ageFilter && nameFilter) || statusFilter

            assert(complexFilter("name" ~ "John" & "age" ~ 25 & "status" ~ "inactive"))
            assert(complexFilter("name" ~ "Mary" & "age" ~ 40 & "status" ~ "active"))
            assert(!complexFilter("name" ~ "Mary" & "age" ~ 40 & "status" ~ "inactive"))
        }

        "multiple conditions on same field" in {
            val filter = (~"age" > 20 && ~"age" < 30) || (~"age" > 50)
            assert(filter("name" ~ "John" & "age" ~ 25))
            assert(filter("name" ~ "Bob" & "age" ~ 55))
            assert(!filter("name" ~ "Alice" & "age" ~ 40))
        }
    }

    "numeric edge cases" - {
        "maximum values" in {
            val filter = ~"value" <= Double.MaxValue
            assert(filter("name" ~ "Test" & "value" ~ 1.7976931348623157e308))
        }

        "minimum values" in {
            val filter = ~"value" >= Double.MinValue
            assert(filter("name" ~ "Test" & "value" ~ 4.9e-324))
        }

        "zero comparison" in {
            val filter = ~"value" > 0.0
            assert(filter("name" ~ "Test" & "value" ~ 0.1))
            assert(!filter("name" ~ "Test" & "value" ~ 0.0))
            assert(!filter("name" ~ "Test" & "value" ~ -0.1))
        }
    }

end FilterTest
