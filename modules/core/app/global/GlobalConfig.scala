package global

import controllers.base.LoginHandler


trait MenuConfig {
  val mainSection: Iterable[(String,String)]
  val adminSection: Iterable[(String,String)]
}

trait GlobalConfig {
  val menuConfig: MenuConfig
  val loginHandler: LoginHandler
  val searchDispatcher: utils.search.Dispatcher
  val routeRegistry: RouteRegistry
}
