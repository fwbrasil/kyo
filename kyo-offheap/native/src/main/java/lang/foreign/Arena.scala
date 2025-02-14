package java.lang.foreign

import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib.{malloc, free}
import java.util.concurrent.ConcurrentLinkedQueue

/** A minimal stub for Java's Arena using Scala Native.
  *
  * An Arena is used to track off-heap allocations so that they can be
  * automatically deallocated when the scope ends.
  */
final class Arena extends AutoCloseable {
  private val allocations = new ConcurrentLinkedQueue[Ptr[Byte]]()

  /** Allocates a block of off-heap memory of the given size (in bytes).
    *
    * @param byteSize The size in bytes to allocate.
    * @return A MemorySegment representing the allocated block.
    */
  def allocate(byteSize: Long): MemorySegment = {
    val p = malloc(byteSize.toULong).asInstanceOf[Ptr[Byte]]
    if (p == null) throw new OutOfMemoryError("malloc failed")
    allocations.add(p)
    new MemorySegment(p, byteSize)
  }

  /** Frees all memory that was allocated through this Arena.
    */
  override def close(): Unit = {
    var ptr = allocations.poll()
    while (ptr != null) {
      free(ptr)
      ptr = allocations.poll()
    }
  }
}

object Arena {
  /** Shared Arena.
    *
    * This is used by the shared Memory API to obtain an Arena for allocation.
    */
  def ofShared(): Arena = new Arena()
}