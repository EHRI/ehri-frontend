package services.search

import com.google.inject.ImplementedBy

@ImplementedBy(classOf[NoopSearchLogger])
trait SearchLogger {
  def log(params: => ParamLog): Unit
}

class NoopSearchLogger extends SearchLogger {
  override def log(params: => ParamLog): Unit = ()
}
