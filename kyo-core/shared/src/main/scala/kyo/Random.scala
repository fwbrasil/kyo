package kyo

import kyo.internal.Trace
import scala.annotation.tailrec

trait Random:
    def nextInt(using Trace): Int < IOs
    def nextInt(exclusiveBound: Int)(using Trace): Int < IOs
    def nextLong(using Trace): Long < IOs
    def nextDouble(using Trace): Double < IOs
    def nextBoolean(using Trace): Boolean < IOs
    def nextFloat(using Trace): Float < IOs
    def nextGaussian(using Trace): Double < IOs
    def nextValue[T](seq: Seq[T])(using Trace): T < IOs
    def nextValues[T](length: Int, seq: Seq[T])(using Trace): Seq[T] < IOs
    def nextStringAlphanumeric(length: Int)(using Trace): String < IOs
    def nextString(length: Int, chars: Seq[Char])(using Trace): String < IOs
    def nextBytes(length: Int)(using Trace): Seq[Byte] < IOs
    def shuffle[T](seq: Seq[T])(using Trace): Seq[T] < IOs
    def unsafe: Random.Unsafe
end Random

object Random:

    trait Unsafe:
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

    val default = apply(Unsafe(new java.util.Random))

    def apply(u: Unsafe): Random =
        new Random:
            def nextInt(using Trace): Int < IOs                      = IOs(u.nextInt)
            def nextInt(exclusiveBound: Int)(using Trace): Int < IOs = IOs(u.nextInt(exclusiveBound))
            def nextLong(using Trace): Long < IOs                    = IOs(u.nextLong)
            def nextDouble(using Trace): Double < IOs                = IOs(u.nextDouble)
            def nextBoolean(using Trace): Boolean < IOs              = IOs(u.nextBoolean)
            def nextFloat(using Trace): Float < IOs                  = IOs(u.nextFloat)
            def nextGaussian(using Trace): Double < IOs              = IOs(u.nextGaussian)
            def nextValue[T](seq: Seq[T])(using Trace): T < IOs      = IOs(u.nextValue(seq))
            def nextValues[T](length: Int, seq: Seq[T])(using Trace): Seq[T] < IOs =
                IOs(u.nextValues(length, seq))
            def nextStringAlphanumeric(length: Int)(using Trace): String < IOs =
                IOs(u.nextStringAlphanumeric(length))
            def nextString(length: Int, chars: Seq[Char])(using Trace): String < IOs =
                IOs(u.nextString(length, chars))
            def nextBytes(length: Int)(using Trace): Seq[Byte] < IOs =
                IOs(u.nextBytes(length))
            def shuffle[T](seq: Seq[T])(using Trace): Seq[T] < IOs =
                IOs(u.shuffle(seq))
            def unsafe: Unsafe = u
end Random

object Randoms:

    private val local = Locals.init(Random.default)

    def let[T, S](r: Random)(v: T < S)(using Trace): T < (S & IOs) =
        local.let(r)(v)

    def nextInt(using Trace): Int < IOs =
        local.use(_.nextInt)

    def nextInt(exclusiveBound: Int)(using Trace): Int < IOs =
        local.use(_.nextInt(exclusiveBound))

    def nextLong(using Trace): Long < IOs =
        local.use(_.nextLong)

    def nextDouble(using Trace): Double < IOs =
        local.use(_.nextDouble)

    def nextBoolean(using Trace): Boolean < IOs =
        local.use(_.nextBoolean)

    def nextFloat(using Trace): Float < IOs =
        local.use(_.nextFloat)

    def nextGaussian(using Trace): Double < IOs =
        local.use(_.nextGaussian)

    def nextValue[T](seq: Seq[T])(using Trace): T < IOs =
        local.use(_.nextValue(seq))

    def nextValues[T](length: Int, seq: Seq[T])(using Trace): Seq[T] < IOs =
        local.use(_.nextValues(length, seq))

    def nextStringAlphanumeric(length: Int)(using Trace): String < IOs =
        local.use(_.nextStringAlphanumeric(length))

    def nextString(length: Int, chars: Seq[Char])(using Trace): String < IOs =
        local.use(_.nextString(length, chars))

    def nextBytes(length: Int)(using Trace): Seq[Byte] < IOs =
        local.use(_.nextBytes(length))

    def shuffle[T](seq: Seq[T])(using Trace): Seq[T] < IOs =
        local.use(_.shuffle(seq))
end Randoms
