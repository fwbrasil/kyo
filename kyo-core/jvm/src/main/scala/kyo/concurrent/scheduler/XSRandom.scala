package kyo.concurrent.scheduler

import java.util.Random

private object XSRandom extends Random {
  private[this] val seeds = List.fill(32)(31L).toArray
  override def next(nbits: Int): Int = {
    val idx = (Thread.currentThread().getId & 31).toInt
    var x   = seeds(idx)
    x ^= (x << 21)
    x ^= (x >>> 35)
    x ^= (x << 4)
    seeds(idx) = x
    x &= ((1L << nbits) - 1)
    x.asInstanceOf[Int]
  }
}
