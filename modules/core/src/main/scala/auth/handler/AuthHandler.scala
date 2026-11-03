package auth.handler

import auth.handler.cookie.CookieAuthHandler
import com.google.inject.ImplementedBy
import models.Account
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}


/**
  * Authentication handler.
  *
  * Derived in large part from play2-auth:
  *
  * https://github.com/t2v/play2-auth.git
  *
  * Modified for Play 2.5+.
  */
@ImplementedBy(classOf[CookieAuthHandler])
trait AuthHandler {

  protected def tokenAccessor: TokenAccessor

  type ResultUpdater = Result => Result

  def restoreAccount(implicit request: RequestHeader): Future[(Option[Account], ResultUpdater)]

  def newSession(account: Account): String

  def login(account: Account, result: => Future[Result])(implicit request: RequestHeader, executionContext: ExecutionContext): Future[Result] =
    result.map(tokenAccessor.put(newSession(account)))

  def logout(result: => Future[Result])(implicit request: RequestHeader, executionContext: ExecutionContext): Future[Result] =
    result.map(tokenAccessor.delete)
}
