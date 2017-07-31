package services.search

trait SearchLogger {
  def log(params: ParamLog): Unit
}
