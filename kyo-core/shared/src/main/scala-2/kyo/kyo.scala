import scala.language.higherKinds

package object kyo {

  type <[+T, -S] >: T // = T | Kyo[_, _, _, T, S]

  class KyoOps[+T, -S](private[kyo] val v: T < S) extends AnyVal {

    def flatMap[U, S2](f: T => U < S2): U < (S with S2) =
      kyo.core.transform(v)(f)

    def map[U, S2](f: T => U < S2): U < (S with S2) =
      flatMap(f)

    def unit: Unit < S =
      map(_ => ())

    def withFilter(p: T => Boolean): T < S =
      map(v => if (!p(v)) throw new MatchError(v) else v)

    def flatten[U, S2](implicit ev: T => U < S2): U < (S with S2) =
      flatMap(ev)

    def andThen[U, S2](f: => U < S2)(implicit ev: T => Unit): U < (S with S2) =
      flatMap(_ => f)

    def repeat(i: Int)(implicit ev: T => Unit): Unit < S =
      if (i <= 0) () else andThen(repeat(i - 1))
  }

  implicit class KyoPureOps[+T](private[kyo] val v: T < Any) extends AnyVal {

    def pure: T = v.asInstanceOf[T]
  }

  implicit def kyoOps[T, U, S](v: T)(implicit
      ev: T => U < S,
      ng: NotGiven[Any => S]
  ): KyoOps[U, S] =
    new KyoOps[U, S](v)

  def zip[T1, T2, S](v1: T1 < S, v2: T2 < S): (T1, T2) < S =
    v1.map(t1 => v2.map(t2 => (t1, t2)))

  def zip[T1, T2, T3, S](v1: T1 < S, v2: T2 < S, v3: T3 < S): (T1, T2, T3) < S =
    v1.map(t1 => v2.map(t2 => v3.map(t3 => (t1, t2, t3))))

  def zip[T1, T2, T3, T4, S](
      v1: T1 < S,
      v2: T2 < S,
      v3: T3 < S,
      v4: T4 < S
  ): (T1, T2, T3, T4) < S =
    v1.map(t1 => v2.map(t2 => v3.map(t3 => v4.map(t4 => (t1, t2, t3, t4)))))
}
