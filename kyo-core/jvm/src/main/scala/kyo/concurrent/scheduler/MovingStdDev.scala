package kyo.concurrent.scheduler

private final class MovingStdDev(exp: Int) {

  private val window = 1 << exp
  private val mask   = window - 1
  private var values = new Array[Long](window)
  private var devs   = new Array[Long](window)
  private var idx    = 0
  private var sum    = 0L
  private var sumDev = 0L

  @volatile private var _avg = 0L
  @volatile private var _dev = 0L

  def dev(): Long = _dev
  def avg(): Long = _avg

  def observe(v: Long): Unit =
    val prev = values(idx)
    values(idx) = v
    sum = sum - prev + v
    _avg = sum >> exp

    val currDev = Math.abs((v - _avg) << 1)
    val prevDev = devs(idx)
    devs(idx) = currDev
    sumDev = sumDev - prevDev + currDev
    _dev = Math.sqrt((sumDev >> exp).toDouble).toInt

    idx = (idx + 1) & mask

  override def toString = s"MovingStdDev(avg=${_avg},dev=${_dev})"

}
