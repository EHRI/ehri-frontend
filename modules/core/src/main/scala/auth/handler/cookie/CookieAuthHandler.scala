package auth.handler.cookie

import auth.handler.{AuthHandler, SessionToken, TokenAccessor}
import models.Account
import play.api.mvc.RequestHeader
import play.api.{Configuration, Environment, Mode}
import services.accounts.AccountManager

import javax.inject.Inject
import scala.concurrent.duration.FiniteDuration
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
case class CookieAuthHandler @Inject()(
  env: Environment,
  tokenAccessor: TokenAccessor,
  accountManager: AccountManager,
  config: Configuration,
  executionContext: ExecutionContext
) extends AuthHandler {

  implicit val ec: ExecutionContext = executionContext

  private val sessionTimeout: FiniteDuration = config.get[FiniteDuration]("auth.session.timeout")
  private val sessionMaxAge: FiniteDuration = config.get[FiniteDuration]("auth.session.maxAge")

  private def now: Long = System.currentTimeMillis() / 1000

  override def newSession(account: Account): String =
    SessionToken(account.id, account.sessionVersion, now, now).encode

  override def restoreAccount(implicit request: RequestHeader): Future[(Option[Account], ResultUpdater)] = {
    extractToken(request).map { value =>
      SessionToken.parse(value).fold(immediateLogout) { token =>
        accountManager.findById(token.userId).map {
          case Some(account) if isValid(token, account) =>
            Some(account) -> unlessSet(r => tokenAccessor.put(token.copy(lastSeen = now).encode)(r))
          case _ => None -> unlessSet(r => tokenAccessor.delete(r))
        }
      }
    }.getOrElse(Future.successful(Option.empty -> identity))
  }

  private def immediateLogout(implicit request: RequestHeader): Future[(Option[Account], ResultUpdater)] =
    Future.successful(None -> unlessSet(r => tokenAccessor.delete(r)))

  // Don't replace a token set by the action, e.g. on login
  private def unlessSet(update: ResultUpdater): ResultUpdater =
    result => if (tokenAccessor.setsToken(result)) result else update(result)

  private def isValid(token: SessionToken, account: Account): Boolean = {
    val time = now
    token.version == account.sessionVersion &&
      time - token.issuedAt < sessionMaxAge.toSeconds &&
      time - token.lastSeen < sessionTimeout.toSeconds
  }

  private[auth] def extractToken(request: RequestHeader): Option[String] = {
    if (env.mode == Mode.Test) {
      request.headers.get("PLAY2_AUTH_TEST_TOKEN") orElse tokenAccessor.extract(request)
    } else {
      tokenAccessor.extract(request)
    }
  }
}
