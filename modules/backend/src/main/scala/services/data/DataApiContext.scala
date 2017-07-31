package services.data

import scala.concurrent.ExecutionContext

/**
 * Context required for backend handles:
 *  - the event handler
 *  - the user
 *  - the execution context
 */
trait DataApiContext {
  def eventHandler: EventHandler
  implicit def apiUser: ApiUser
  implicit def executionContext: ExecutionContext
}
