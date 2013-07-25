package global

import java.util
import controllers.base.LoginHandler


trait GlobalConfig {
  val menuConfig: MenuConfig
  val loginHandler: LoginHandler
  val searchDispatcher: utils.search.Dispatcher
}

trait MenuConfig {
  val mainSection: Iterable[(String,String)]
  val adminSection: Iterable[(String,String)]
}