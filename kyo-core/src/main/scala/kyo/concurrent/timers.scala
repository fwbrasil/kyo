package kyo.concurrent

import kyo.core._
import kyo.envs._
import kyo.ios._

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

import scheduler.ThreadFactory

object timers {

  trait Timer {
    def shutdown: Unit > IOs
    def schedule(delay: Duration)(f: => Unit > IOs): TimerTask > IOs
    def scheduleAtFixedRate(
        initalDelay: Duration,
        period: Duration
    )(f: => Unit > IOs): TimerTask > IOs
    def scheduleWithFixedDelay(
        initalDelay: Duration,
        period: Duration
    )(f: => Unit > IOs): TimerTask > IOs
  }

  object Timer {
    given default: Timer = {
      new Timer {
        private val exec =
          Executors.newScheduledThreadPool(
              Runtime.getRuntime.availableProcessors / 2,
              ThreadFactory("kyo-timer-default")
          )

        private final class Task[T](task: ScheduledFuture[T]) extends TimerTask {
          def cancel: Boolean > IOs      = IOs(task.cancel(false))
          def isCancelled: Boolean > IOs = IOs(task.isCancelled())
          def isDone: Boolean > IOs      = IOs(task.isDone())
        }

        def shutdown: Unit > IOs =
          IOs.unit

        def schedule(delay: Duration)(f: => Unit > IOs): TimerTask > IOs =
          if (delay.isFinite) {
            IOs(Task(exec.schedule(() => IOs.run(f), delay.toNanos, TimeUnit.NANOSECONDS)))
          } else {
            TimerTask.noop
          }

        def scheduleAtFixedRate(
            initalDelay: Duration,
            period: Duration
        )(f: => Unit > IOs): TimerTask > IOs =
          if (period.isFinite && initalDelay.isFinite) {
            IOs(Task {
              exec.scheduleAtFixedRate(
                  () => IOs.run(f),
                  initalDelay.toNanos,
                  period.toNanos,
                  TimeUnit.NANOSECONDS
              )
            })
          } else {
            TimerTask.noop
          }

        def scheduleWithFixedDelay(
            initalDelay: Duration,
            period: Duration
        )(f: => Unit > IOs): TimerTask > IOs =
          if (period.isFinite && initalDelay.isFinite) {
            IOs(Task {
              exec.scheduleWithFixedDelay(
                  () => IOs.run(f),
                  initalDelay.toNanos,
                  period.toNanos,
                  TimeUnit.NANOSECONDS
              )
            })
          } else {
            TimerTask.noop
          }
      }
    }
  }

  trait TimerTask {
    def cancel: Boolean > IOs
    def isCancelled: Boolean > IOs
    def isDone: Boolean > IOs
  }

  object TimerTask {
    private[kyo] val noop = new TimerTask {
      def cancel      = false
      def isCancelled = false
      def isDone      = true
    }
  }

  opaque type Timers = Envs[Timer]

  object Timers {
    def run[T, S](t: Timer)(f: => T > (S | Timers)): T > S =
      Envs[Timer].let(t)(f)
    def run[T, S](f: => T > (S | Timers))(using t: Timer): T > S =
      Envs[Timer].let(t)(f)
    def shutdown: Unit > (Timers | IOs) =
      Envs[Timer].get(_.shutdown)
    def schedule(delay: Duration)(f: => Unit > IOs): TimerTask > (Timers | IOs) =
      Envs[Timer].get(_.schedule(delay)(f))
    def scheduleAtFixedRate(
        period: Duration
    )(f: => Unit > IOs): TimerTask > (Timers | IOs) =
      scheduleAtFixedRate(Duration.Zero, period)(f)
    def scheduleAtFixedRate(
        initialDelay: Duration,
        period: Duration
    )(f: => Unit > IOs): TimerTask > (Timers | IOs) =
      Envs[Timer].get(_.scheduleAtFixedRate(initialDelay, period)(f))
    def scheduleWithFixedDelay(
        period: Duration
    )(f: => Unit > IOs): TimerTask > (Timers | IOs) =
      scheduleWithFixedDelay(Duration.Zero, period)(f)
    def scheduleWithFixedDelay(
        initialDelay: Duration,
        period: Duration
    )(f: => Unit > IOs): TimerTask > (Timers | IOs) =
      Envs[Timer].get(_.scheduleWithFixedDelay(initialDelay, period)(f))
  }
}
