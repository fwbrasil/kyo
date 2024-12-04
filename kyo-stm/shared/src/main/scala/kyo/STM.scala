package kyo

import scala.annotation.tailrec

/** A FailedTransaction exception that is thrown when a transaction fails to commit. Contains the frame where the failure occurred.
  */
case class FailedTransaction(frame: Frame) extends Exception(frame.position.show)

/** Software Transactional Memory (STM) provides concurrent access to shared state using optimistic locking. Rather than acquiring locks
  * upfront, transactions execute speculatively and automatically retry if conflicts are detected during commit. While this enables better
  * composability than manual locking, applications must be designed to handle potentially frequent transaction retries.
  *
  * > IMPORTANT: Transactions are atomic, isolated, and composable but may retry multiple times before success. Side effects (like I/O)
  * inside transactions must be used with caution as they will be re-executed on retry. Pure operations that only modify transactional
  * references are safe and encouraged, while external side effects should be performed after the transaction commits.
  *
  * The core operations are:
  *   - TRef.init and TRef.initNow create transactional references that can be shared between threads
  *   - TRef.get and TRef.set read and modify references within transactions
  *   - STM.run executes transactions that either fully commit or rollback
  *   - STM.retry and STM.retryIf provide manual control over transaction retry behavior
  *   - Configurable retry schedules via STM.run's retrySchedule parameter
  *
  * The implementation uses optimistic execution with lock-based validation during commit:
  *   - Transactions execute without acquiring locks, tracking reads and writes in a local log
  *   - During commit, read-write locks are acquired on affected TRefs to ensure consistency:
  *     - Multiple readers can hold shared locks on a TRef during commit
  *     - Writers require an exclusive lock during commit
  *     - No global locks are used - operations on different refs can commit independently
  *     - Lock acquisition is ordered by TRef identity to prevent deadlocks
  *     - Early conflict detection aborts transactions that would fail validation
  *
  * STM is most effective for operations that rarely conflict and complete quickly. Long-running transactions or high contention scenarios
  * may face performance challenges from repeated retries. The approach particularly excels at read-heavy workloads due to its support for
  * concurrent readers, while write-heavy workloads may experience more contention due to the need for exclusive write access. The
  * fine-grained locking strategy means that transactions only conflict if they actually touch the same references, allowing for high
  * concurrency when different transactions operate on different refs.
  */
opaque type STM <: (Var[TRefLog] & Abort[FailedTransaction] & Async) =
    Var[TRefLog] & Abort[FailedTransaction] & Async

object STM:

    /** The default retry schedule for failed transactions */
    val defaultRetrySchedule = Schedule.fixed(1.millis * 0.5).take(20)

    /** Forces a transaction retry by aborting the current transaction and rolling back all changes. This is useful when a transaction
      * detects that it cannot proceed due to invalid state.
      *
      * @return
      *   Nothing, as this operation always aborts the transaction
      */
    def retry(using frame: Frame): Nothing < STM = Abort.fail(FailedTransaction(frame))

    /** Conditionally retries a transaction based on a boolean condition. If the condition is true, the transaction will be retried.
      * Otherwise, execution continues normally.
      *
      * @param cond
      *   The condition that determines whether to retry
      */
    def retryIf(cond: Boolean)(using frame: Frame): Unit < STM = Abort.when(cond)(FailedTransaction(frame))

    /** Executes a transactional computation with explicit state isolation. This version of run supports additional effects beyond Abort and
      * Async through the provided isolate, which ensures proper state management during transaction retries and rollbacks.
      *
      * @param isolate
      *   The isolation scope for the transaction
      * @param retrySchedule
      *   The schedule for retrying failed transactions
      * @param v
      *   The transactional computation to run
      * @return
      *   The result of the computation if successful
      */
    def run[E, A: Flat, S](isolate: Isolate[S], retrySchedule: Schedule = defaultRetrySchedule)(v: A < (STM & Abort[E] & Async & S))(
        using frame: Frame
    ): A < (S & Async & Abort[E | FailedTransaction]) =
        isolate.use { st =>
            run(retrySchedule)(isolate.resume(st, v)).map(isolate.restore(_, _))
        }

    /** Executes a transactional computation with default retry behavior. This version only supports Abort and Async effects within the
      * transaction, but provides a simpler interface when additional effect isolation is not needed.
      *
      * @param v
      *   The transactional computation to run
      * @return
      *   The result of the computation if successful
      */
    def run[E, A: Flat](v: A < (STM & Abort[E] & Async))(using frame: Frame): A < (Async & Abort[E | FailedTransaction]) =
        run(defaultRetrySchedule)(v)

    /** Executes a transactional computation with custom retry behavior. Like the version above, this only supports Abort and Async effects
      * but allows configuring how transaction conflicts are retried.
      *
      * @param retrySchedule
      *   The schedule for retrying failed transactions
      * @param v
      *   The transactional computation to run
      * @return
      *   The result of the computation if successful
      */
    def run[E, A: Flat](retrySchedule: Schedule)(v: A < (STM & Abort[E] & Async))(
        using frame: Frame
    ): A < (Async & Abort[E | FailedTransaction]) =
        TID.use {
            case -1L =>
                // New transaction without a parent, use regular commit flow
                Retry[FailedTransaction](retrySchedule) {
                    TID.useNew { tid =>
                        TRefLog.runWith(v) { (log, result) =>
                            IO.Unsafe {
                                // Attempt to acquire locks and commit the transaction
                                val (locked, unlocked) =
                                    // Sort references by identity to prevent deadlocks
                                    log.toSeq.sortBy((ref, _) => ref.hashCode)
                                        .span((ref, entry) => ref.lock(entry))

                                if unlocked.nonEmpty then
                                    // Failed to acquire some locks - rollback and retry
                                    locked.foreach((ref, entry) => ref.unlock(entry))
                                    Abort.fail(FailedTransaction(frame))
                                else
                                    // Successfully locked all references - commit changes
                                    locked.foreach((ref, entry) => ref.commit(tid, entry))
                                    // Release all locks
                                    locked.foreach((ref, entry) => ref.unlock(entry))
                                    result
                                end if
                            }
                        }
                    }
                }
            case parent =>
                // Nested transaction inherits parent's transaction context but isolates RefLog.
                // On success: changes propagate to parent. On failure: changes are rolled back
                // without affecting parent's state.
                val result = TRefLog.isolate(v)

                // Can't return `result` directly since it has a pending STM effect
                // but it's safe to cast because, if there's a parent transaction,
                // then there's a frame upper in the stack that will handle the
                // STM effect in the parent transaction's `run`.
                result.asInstanceOf[A < (Async & Abort[E | FailedTransaction])]
        }

    end run
end STM
