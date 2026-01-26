package lifecycle

import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.Marker

/**
  * Prevent a flood of errors from the application when it is shutting down
  * whilst requests are still in flight. These are mainly thrown from the
  * Netty backend to the WS HTTP client class which throws an IllegalStateException.
  *
  * NB: this doesn't work with the play dev server.
  */
class ShutdownNoiseFilter extends TurboFilter {
  override def decide(
    marker: Marker,
    logger: Logger,
    level: Level,
    format: String,
    params: Array[AnyRef],
    t: Throwable
  ): FilterReply = {

    if (t != null && isShutdownError(t)) {
      FilterReply.DENY // Drop the log entry entirely
    } else {
      FilterReply.NEUTRAL // Let other filters or loggers decide
    }
  }

  private def isShutdownError(t: Throwable): Boolean = {
    // Check the exception and its causes for the "Closed" message
    val message = Option(t.getMessage).getOrElse("")
    val causeMessage = Option(t.getCause).map(_.getMessage).getOrElse("")

    (message.contains("Closed") || causeMessage.contains("Closed")) &&
      t.isInstanceOf[IllegalStateException]
  }
}
