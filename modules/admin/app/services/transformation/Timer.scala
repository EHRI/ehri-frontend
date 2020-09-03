package services.transformation

import play.api.Logger

trait Timer {

  protected def logger: Logger

  protected def time[R](op: String)(block: => R): R = {
    import java.time.{Duration, Instant}

    val t0 = Instant.now()
    val result = block
    val t1 = Instant.now()
    val ellapsed = Duration.between(t0, t1)
    logger.debug(s"$op: Elapsed time: ${ellapsed.toMillis}")
    result
  }
}
