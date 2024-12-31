package kyo

import Record.Field

sealed abstract class Filter[+Fields]

export Filter.unary_~

object Filter:

    case class Or[A, B](a: Filter[A], b: Filter[B])                           extends Filter[A & B]
    case class And[A, B](a: Filter[A], b: Filter[B])                          extends Filter[A & B]
    case class Not[A](filter: Filter[A])                                      extends Filter[A]
    case class Predicate[Name <: String, T](field: Field[Name, T], op: Op[T]) extends Filter[Name ~ T]

    sealed abstract class Op[T]:
        def apply(v: T): Boolean

    object Op:
        case class Eq[A](value: A)(using CanEqual[A, A]) extends Op[A]:
            def apply(v: A) = v == value

        case class Gt[A: Numeric](value: A) extends Op[A]:
            def apply(v: A) = Numeric[A].compare(v, value) > 0

        case class Lt[A: Numeric](value: A) extends Op[A]:
            def apply(v: A) = Numeric[A].compare(v, value) < 0

        case class Gte[A: Numeric](value: A) extends Op[A]:
            def apply(v: A) = Numeric[A].compare(v, value) >= 0

        case class Lte[A: Numeric](value: A) extends Op[A]:
            def apply(v: A) = Numeric[A].compare(v, value) <= 0

        case class Like(pattern: String) extends Op[String]:
            val regex = pattern
                .replace(".", "\\.")
                .replace("%", ".*")
                .replace("_", ".")
            def apply(v: String): Boolean =
                v.matches(regex)
        end Like

        case class In[A](values: Set[A])(using CanEqual[A, A]) extends Op[A]:
            def apply(v: A) = values.exists(_ == v)

        case class Between[A: Numeric](start: A, end: A) extends Op[A]:
            def apply(v: A) =
                val num = Numeric[A]
                num.compare(v, start) >= 0 && num.compare(v, end) <= 0
        end Between
    end Op

    case class FilterOps[Name <: String](name: Name) extends AnyVal:
        private def predicate[A: Tag](op: Op[A]): Filter[Name ~ A] =
            Predicate(Field(name, Tag[A]), op)

        infix def >[A: Numeric: Tag](value: A): Filter[Name ~ A] =
            predicate(Op.Gt(value))

        infix def >=[A: Numeric: Tag](value: A): Filter[Name ~ A] =
            predicate(Op.Gte(value))

        infix def <[A: Numeric: Tag](value: A): Filter[Name ~ A] =
            predicate(Op.Lt(value))

        infix def <=[A: Numeric: Tag](value: A): Filter[Name ~ A] =
            predicate(Op.Lte(value))

        infix def ==[A: Tag](value: A)(using CanEqual[A, A]): Filter[Name ~ A] =
            predicate(Op.Eq(value))

        infix def like(pattern: String): Filter[Name ~ String] =
            predicate(Op.Like(pattern))

        infix def in[A: Tag](values: A*)(using CanEqual[A, A]): Filter[Name ~ A] =
            predicate(Op.In(values.toSet))

        infix def between[A: Numeric: Tag](start: A, end: A): Filter[Name ~ A] =
            predicate(Op.Between(start, end))

    end FilterOps

    extension (self: String)
        def unary_~ : FilterOps[self.type] = FilterOps(self)

    extension [F1](self: Filter[F1])
        def &&[F2](that: Filter[F2]): Filter[F1 & F2] = And(self, that)
        def ||[F2](that: Filter[F2]): Filter[F1 & F2] = Or(self, that)
        def unary_! : Filter[F1]                      = Not(self)

        def apply[F2](record: Record[F2])(using F2 <:< F1): Boolean =
            def loop[F](filter: Filter[F]): Boolean =
                filter match
                    case And(left, right)     => loop(left) && loop(right)
                    case Or(left, right)      => loop(left) || loop(right)
                    case Not(filter)          => !loop(filter)
                    case Predicate(field, op) => op(record.toMap(field).asInstanceOf)
            loop(self)
        end apply
    end extension
end Filter
