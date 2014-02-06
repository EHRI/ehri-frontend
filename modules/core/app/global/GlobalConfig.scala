package global

import play.api.Play.current


trait MenuConfig {
  val mainSection: Iterable[(String,String)]
  val adminSection: Iterable[(String,String)]
  val authSection: Iterable[(String,String)]
}

trait GlobalConfig {

  val menuConfig: MenuConfig
  val routeRegistry: RouteRegistry

  /**
   * Flag to indicate whether we're running a testing config or not.
   * This is different from the usual dev/prod run configuration because
   * we might be running experimental stuff on a real life server.
   */
  def isTestMode = current.configuration.getBoolean("ehri.testing").getOrElse(true)
  def isStageMode = current.configuration.getBoolean("ehri.staging").getOrElse(false)

  lazy val languages: Seq[String] = current.configuration
        .getString("application.langs").map(_.split(",").toSeq).getOrElse(Nil)
}
