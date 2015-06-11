package global

import backend.rest.RestBackend
import play.api.cache.CacheApi
import backend.{BackendHandle, ApiUser, Backend, EventHandler}
import com.google.inject.Inject
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

case class GlobalBackend @Inject()(eventHandler: EventHandler, cache: CacheApi, config: play.api.Configuration, ws: WSClient) extends Backend {
  val rb = new RestBackend(eventHandler, cache, config, ws)

  override def withContext(apiUser: ApiUser)(implicit executionContext: ExecutionContext): BackendHandle = rb.withContext(apiUser)
}
