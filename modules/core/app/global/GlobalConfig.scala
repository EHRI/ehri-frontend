package global

import controllers.base.LoginHandler

import play.api.Play.current


trait MenuConfig {
  val mainSection: Iterable[(String,String)]
  val adminSection: Iterable[(String,String)]
}

trait GlobalConfig {
  val menuConfig: MenuConfig
  val loginHandler: LoginHandler
  val searchDispatcher: utils.search.Dispatcher
  val routeRegistry: RouteRegistry

  /**
   * Flag to indicate whether we're running a testing config or not.
   * This is different from the usual dev/prod run configuration because
   * we might be running experimental stuff on a real life server.
   * @return
   */
  def isTestMode = current.configuration.getBoolean("ehri.testing").getOrElse(true)
}
