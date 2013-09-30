package global

import controllers.base.LoginHandler

import play.api.Play.current
import rest.RestEventHandler


trait MenuConfig {
  val mainSection: Iterable[(String,String)]
  val adminSection: Iterable[(String,String)]
  val authSection: Iterable[(String,String)]
}

trait GlobalConfig {
  val menuConfig: MenuConfig
  val routeRegistry: RouteRegistry
  val eventHandler: RestEventHandler

  /**
   * Flag to indicate whether we're running a testing config or not.
   * This is different from the usual dev/prod run configuration because
   * we might be running experimental stuff on a real life server.
   * @return
   */
  def isTestMode = current.configuration.getBoolean("ehri.testing").getOrElse(true)
}
