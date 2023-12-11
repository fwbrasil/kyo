package kyo

import scala.reflect.ClassTag
import internal.FlatImplicits

sealed trait Flat[-T]

object Flat extends FlatImplicits {

  sealed trait Checked[T]   extends Flat[T]
  sealed trait Unchecked[T] extends Flat[T]

  private val cachedChecked   = new Checked[Any] {}
  private val cachedUnchecked = new Unchecked[Any] {}

  implicit def unit[S]: Flat[Unit < S] = unsafe.checked[Unit < S]

  object unsafe {
    def checked[T]: Checked[T] =
      cachedChecked.asInstanceOf[Checked[T]]
    implicit def unchecked[T]: Unchecked[T] =
      cachedUnchecked.asInstanceOf[Unchecked[T]]
  }
}
