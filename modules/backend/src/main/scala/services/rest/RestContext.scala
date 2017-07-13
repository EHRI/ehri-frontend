package services.rest

import services.{ApiUser, EventHandler}

import scala.concurrent.ExecutionContext

/**
 * Context required for backend handles:
 *  - the event handler
 *  - the user
 *  - the execution context
 */
trait RestContext {
  def eventHandler: EventHandler
  implicit def apiUser: ApiUser
  implicit def executionContext: ExecutionContext
}
