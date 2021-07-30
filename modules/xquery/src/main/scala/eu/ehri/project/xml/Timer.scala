package eu.ehri.project.xml


trait Timer {
  def logTime(time: String): Unit

  protected def time[R](op: String)(block: => R): R = {
    import java.time.{Duration, Instant}

    val t0 = Instant.now()
    val result = block
    val t1 = Instant.now()
    val ellapsed = Duration.between(t0, t1)
    logTime(s"$op: Elapsed time: ${ellapsed.toMillis} millis")
    result
  }
}
