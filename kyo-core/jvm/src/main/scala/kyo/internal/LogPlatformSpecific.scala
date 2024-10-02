package kyo.internal

import kyo.AllowUnsafe
import kyo.Frame
import kyo.Log

trait LogPlatformSpecific:
    val unsafe: Log.Unsafe = LogPlatformSpecific.Unsafe.SLF4J("kyo.logs")

object LogPlatformSpecific:

    /* WARNING: Low-level API meant for integrations, libraries, and performance-sensitive code. See AllowUnsafe for more details. */
    object Unsafe:

        object SLF4J:
            def apply(name: String) = new SLF4J(org.slf4j.LoggerFactory.getLogger(name))

        class SLF4J(logger: org.slf4j.Logger) extends Log.Unsafe:
            inline def traceEnabled: Boolean = logger.isTraceEnabled

            inline def debugEnabled: Boolean = logger.isDebugEnabled

            inline def infoEnabled: Boolean = logger.isInfoEnabled

            inline def warnEnabled: Boolean = logger.isWarnEnabled

            inline def errorEnabled: Boolean = logger.isErrorEnabled

            inline def trace(msg: => String)(using frame: Frame, allow: AllowUnsafe): Unit =
                if traceEnabled then logger.trace(s"[${frame.parse.position}] $msg")

            inline def trace(msg: => String, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                if traceEnabled then logger.trace(s"[${frame.parse.position}] $msg", t)

            inline def debug(msg: => String)(using frame: Frame, allow: AllowUnsafe): Unit =
                if debugEnabled then logger.debug(s"[${frame.parse.position}] $msg")

            inline def debug(msg: => String, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                if debugEnabled then logger.debug(s"[${frame.parse.position}] $msg", t)

            inline def info(msg: => String)(using frame: Frame, allow: AllowUnsafe): Unit =
                if infoEnabled then logger.info(s"[${frame.parse.position}] $msg")

            inline def info(msg: => String, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                if infoEnabled then logger.info(s"[${frame.parse.position}] $msg", t)

            inline def warn(msg: => String)(using frame: Frame, allow: AllowUnsafe): Unit =
                if warnEnabled then logger.warn(s"[${frame.parse.position}] $msg")

            inline def warn(msg: => String, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                if warnEnabled then logger.warn(s"[${frame.parse.position}] $msg", t)

            inline def error(msg: => String)(using frame: Frame, allow: AllowUnsafe): Unit =
                if errorEnabled then logger.error(s"[${frame.parse.position}] $msg")

            inline def error(msg: => String, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                if errorEnabled then logger.error(s"[${frame.parse.position}] $msg", t)
        end SLF4J
    end Unsafe
end LogPlatformSpecific
