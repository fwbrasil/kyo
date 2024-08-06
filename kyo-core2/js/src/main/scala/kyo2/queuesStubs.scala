package org.jctools.queues

import java.util.ArrayDeque

class StubQueue[T](capacity: Int) extends ArrayDeque[T]:
    def isFull = size() >= capacity
    override def offer(e: T): Boolean =
        !isFull && super.offer(e)
end StubQueue

case class MpmcArrayQueue[T](capacity: Int) extends StubQueue[T](capacity)

case class MpscArrayQueue[T](capacity: Int) extends StubQueue[T](capacity)

case class SpmcArrayQueue[T](capacity: Int) extends StubQueue[T](capacity)

case class SpscArrayQueue[T](capacity: Int) extends StubQueue[T](capacity)

case class MpmcUnboundedXaddArrayQueue[T](chunkSize: Int) extends ArrayDeque[T] {}

case class MpscUnboundedArrayQueue[T](chunkSize: Int) extends ArrayDeque[T] {}

case class SpscUnboundedArrayQueue[T](chunkSize: Int) extends ArrayDeque[T] {}
