package kyo

import scala.annotation.tailrec

abstract class Random:
    def nextInt(using Frame): Int < IO
    def nextInt(exclusiveBound: Int)(using Frame): Int < IO
    def nextLong(using Frame): Long < IO
    def nextDouble(using Frame): Double < IO
    def nextBoolean(using Frame): Boolean < IO
    def nextFloat(using Frame): Float < IO
    def nextGaussian(using Frame): Double < IO
    def nextValue[T](seq: Seq[T])(using Frame): T < IO
    def nextValues[T](length: Int, seq: Seq[T])(using Frame): Seq[T] < IO
    def nextStringAlphanumeric(length: Int)(using Frame): String < IO
    def nextString(length: Int, chars: Seq[Char])(using Frame): String < IO
    def nextBytes(length: Int)(using Frame): Seq[Byte] < IO
    def shuffle[T](seq: Seq[T])(using Frame): Seq[T] < IO
    def unsafe: Random.Unsafe
end Random

object Random:

    abstract class Unsafe:
        def nextInt: Int
        def nextInt(exclusiveBound: Int): Int
        def nextLong: Long
        def nextDouble: Double
        def nextBoolean: Boolean
        def nextFloat: Float
        def nextGaussian: Double
        def nextValue[T](seq: Seq[T]): T
        def nextValues[T](length: Int, seq: Seq[T]): Seq[T]
        def nextStringAlphanumeric(length: Int): String
        def nextString(length: Int, seq: Seq[Char]): String
        def nextBytes(length: Int): Seq[Byte]
        def shuffle[T](seq: Seq[T]): Seq[T]
    end Unsafe

    object Unsafe:
        def apply(random: java.util.Random): Unsafe =
            new Unsafe:
                def nextInt: Int                      = random.nextInt()
                def nextInt(exclusiveBound: Int): Int = random.nextInt(exclusiveBound)
                def nextLong: Long                    = random.nextLong()
                def nextDouble: Double                = random.nextDouble()
                def nextBoolean: Boolean              = random.nextBoolean()
                def nextFloat: Float                  = random.nextFloat()
                def nextGaussian: Double              = random.nextGaussian()
                def nextValue[T](seq: Seq[T]): T      = seq(random.nextInt(seq.size))
                def nextValues[T](length: Int, seq: Seq[T]): Seq[T] =
                    Seq.fill(length)(nextValue(seq))

                val alphanumeric = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toIndexedSeq
                def nextStringAlphanumeric(length: Int): String =
                    nextString(length, alphanumeric)

                def nextString(length: Int, seq: Seq[Char]): String =
                    val b = new StringBuilder
                    @tailrec def loop(i: Int): Unit =
                        if i < length then
                            b.addOne(nextValue(seq))
                            loop(i + 1)
                    loop(0)
                    b.result()
                end nextString

                val bytes = Seq(0.toByte, 1.toByte).toIndexedSeq
                def nextBytes(length: Int): Seq[Byte] =
                    nextValues(length, bytes)

                def shuffle[T](seq: Seq[T]): Seq[T] =
                    val buffer = scala.collection.mutable.ArrayBuffer.from(seq)
                    @tailrec def shuffleLoop(i: Int): Unit =
                        if i > 0 then
                            val j    = nextInt(i + 1)
                            val temp = buffer(i)
                            buffer(i) = buffer(j)
                            buffer(j) = temp
                            shuffleLoop(i - 1)
                    shuffleLoop(buffer.size - 1)
                    buffer.toSeq
                end shuffle
    end Unsafe

    def apply(u: Unsafe): Random =
        new Random:
            def nextInt(using Frame): Int < IO                      = IO(u.nextInt)
            def nextInt(exclusiveBound: Int)(using Frame): Int < IO = IO(u.nextInt(exclusiveBound))
            def nextLong(using Frame): Long < IO                    = IO(u.nextLong)
            def nextDouble(using Frame): Double < IO                = IO(u.nextDouble)
            def nextBoolean(using Frame): Boolean < IO              = IO(u.nextBoolean)
            def nextFloat(using Frame): Float < IO                  = IO(u.nextFloat)
            def nextGaussian(using Frame): Double < IO              = IO(u.nextGaussian)
            def nextValue[T](seq: Seq[T])(using Frame): T < IO      = IO(u.nextValue[T](seq))
            def nextValues[T](length: Int, seq: Seq[T])(using Frame): Seq[T] < IO =
                IO(u.nextValues(length, seq))
            def nextStringAlphanumeric(length: Int)(using Frame): String < IO =
                IO(u.nextStringAlphanumeric(length))
            def nextString(length: Int, chars: Seq[Char])(using Frame): String < IO =
                IO(u.nextString(length, chars))
            def nextBytes(length: Int)(using Frame): Seq[Byte] < IO =
                IO(u.nextBytes(length))
            def shuffle[T](seq: Seq[T])(using Frame): Seq[T] < IO =
                IO(u.shuffle(seq))
            def unsafe: Unsafe = u

    val live = Random(Random.Unsafe(new java.util.Random))

    private val local = Local.init(live)

    def let[T, S](r: Random)(v: T < S)(using Frame): T < (S & IO) =
        local.let(r)(v)

    def nextInt(using Frame): Int < IO                                      = local.use(_.nextInt)
    def nextInt(exclusiveBound: Int)(using Frame): Int < IO                 = local.use(_.nextInt(exclusiveBound))
    def nextLong(using Frame): Long < IO                                    = local.use(_.nextLong)
    def nextDouble(using Frame): Double < IO                                = local.use(_.nextDouble)
    def nextBoolean(using Frame): Boolean < IO                              = local.use(_.nextBoolean)
    def nextFloat(using Frame): Float < IO                                  = local.use(_.nextFloat)
    def nextGaussian(using Frame): Double < IO                              = local.use(_.nextGaussian)
    def nextValue[T](seq: Seq[T])(using Frame): T < IO                      = local.use(_.nextValue(seq))
    def nextValues[T](length: Int, seq: Seq[T])(using Frame): Seq[T] < IO   = local.use(_.nextValues(length, seq))
    def nextStringAlphanumeric(length: Int)(using Frame): String < IO       = local.use(_.nextStringAlphanumeric(length))
    def nextString(length: Int, chars: Seq[Char])(using Frame): String < IO = local.use(_.nextString(length, chars))
    def nextBytes(length: Int)(using Frame): Seq[Byte] < IO                 = local.use(_.nextBytes(length))
    def shuffle[T](seq: Seq[T])(using Frame): Seq[T] < IO                   = local.use(_.shuffle(seq))

end Random
