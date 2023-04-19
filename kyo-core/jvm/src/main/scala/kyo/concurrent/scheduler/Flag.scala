package kyo.concurrent.scheduler

private object Flag {
  trait Reader[T] {
    def apply(s: String): T
  }
  object Reader {
    given Reader[Int]    = Integer.parseInt(_)
    given Reader[String] = identity(_)
    given Reader[Long]   = java.lang.Long.parseLong(_)
    given Reader[Double] = java.lang.Double.parseDouble(_)
    given [T](using r: Reader[T]): Reader[List[T]] =
      (s: String) => s.split(",").toList.map(r(_))
  }
  def apply[T](name: String, default: T)(using r: Reader[T]) =
    Option(System.getProperty(s"kyo.scheduler.$name"))
      .map(r(_)).getOrElse(default)
}
