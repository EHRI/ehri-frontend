package controllers.core.auth.oauth2

import java.util.UUID

import auth.AuthenticationError
import auth.oauth2.providers.OAuth2Provider
import auth.oauth2.{OAuth2Flow, UserData}
import backend.{AnonymousUser, AuthenticatedUser, Backend}
import controllers.base.CoreActionBuilders
import controllers.core.auth.AccountHelpers
import global.GlobalConfig
import models._
import play.api.Logger
import play.api.Play._
import play.api.cache.Cache
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{Call, Result, _}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
 * Oauth2 login handler implementation, cribbed extensively
 * from SecureSocial.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait OAuth2LoginHandler extends AccountHelpers {

  self: Controller with CoreActionBuilders =>

  def backend: Backend
  def accounts: auth.AccountManager
  def globalConfig: GlobalConfig
  def oAuth2Flow: OAuth2Flow

  def oauth2Providers: Seq[OAuth2Provider]


  private val SessionKey = "sid"

  case class OAuth2Request[A](
    accountOrErr: Either[String, Account],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  private def updateUserInfo(account: Account, userData: UserData): Future[UserProfile] = {
    implicit val apiUser = AuthenticatedUser(account.id)
    userBackend.get[UserProfile](account.id).flatMap { up =>
      userBackend.patch[UserProfile](account.id, Json.obj(
        UserProfileF.NAME -> JsString(userData.name),
        // Only update the user image if it hasn't already been set
        UserProfileF.IMAGE_URL -> JsString(up.model.imageUrl.getOrElse(userData.imageUrl))
      ))
    }
  }

  private def createNewProfile(userData: UserData, provider: OAuth2Provider): Future[Account] = {
    implicit val apiUser = AnonymousUser
    val profileData = Map(
      UserProfileF.NAME -> userData.name,
      UserProfileF.IMAGE_URL -> userData.imageUrl
    )
    for {
      profile <- userBackend.createNewUserProfile[UserProfile](profileData, groups = defaultPortalGroups)
      account <- accounts.create(Account(
        id = profile.id,
        email = userData.email.toLowerCase,
        verified = true,
        staff = false,
        active = true,
        allowMessaging = canMessage
      ))
    } yield account
  }

  private def getOrCreateAccount(provider: OAuth2Provider, userData: UserData): Future[Account] = {
    accounts.oAuth2.findByProviderInfo(userData.providerId, provider.name).flatMap { assocOpt =>
      assocOpt.flatMap(_.user).map { account =>
        Logger.info(s"Found existing association for ${userData.name} -> ${provider.name}")
        for {
          updated <- accounts.update(account.copy(verified = true))
          _ <- updateUserInfo(updated, userData)
        } yield updated
      } getOrElse {
        accounts.findByEmail(userData.email).flatMap { accountOpt =>
          accountOpt.map { account =>
            Logger.info(s"Creating new association for ${userData.name} -> ${provider.name}")
            for {
              updated <- accounts.update(account.copy(verified = true))
              _ <- accounts.oAuth2.addAssociation(updated.id, userData.providerId, provider.name)
              _ <- updateUserInfo(updated, userData)
            } yield updated
          } getOrElse {
            Logger.info(s"Creating new account for ${userData.name} -> ${provider.name}")
            for {
              newAccount <- createNewProfile(userData, provider)
              _ <- accounts.oAuth2.addAssociation(newAccount.id, userData.providerId, provider.name)
            } yield newAccount
          }
        }
      }
    }
  }

  private def checkSessionNonce[A](sessionId: String, state: Option[String])(implicit request: Request[A]): Boolean = {
    val origStateOpt: Option[String] = Cache.getAs[String](sessionId)
    (for {
    // check if the state we sent is equal to the one we're receiving now before continuing the flow.
      originalState <- origStateOpt
      currentState <- state
    } yield {
      val check = originalState == currentState
      if (!check) Logger.error(s"OAuth2 state mismatch: sessionId: $sessionId, " +
        s"original token: $origStateOpt, new token: $state")
      check
    }).getOrElse {
      Logger.error(s"Missing OAuth2 state data: session key -> $sessionId")
      false
    }
  }

  /**
   * Log a user in (or register them) via a third party OAuth2 service.
   *
   * @param providerName the provider name, matching one of the supported
   *                     providers in `oauth2Providers`.
   * @param code  the access code returned by the provider. This should be
   *              absent in the first phase
   * @param state the session track state we create, pass to the client, and
   *              expect back again. This should also be empty in the first
   *              phase.
   * @param handler the call from which we are being invoked
   */
  def OAuth2LoginAction(providerName: String, code: Option[String], state: Option[String], handler: Call) = new ActionBuilder[OAuth2Request] {
    override def invokeBlock[A](request: Request[A], block: (OAuth2Request[A]) => Future[Result]): Future[Result] = {
      oauth2Providers.find(_.name == providerName).map { provider =>
        implicit val r = request

        // Create a random nonce to stamp this OAuth2 session
        val sessionId = request.session.get(SessionKey).getOrElse(UUID.randomUUID().toString)
        val handlerUrl: String = handler.absoluteURL(globalConfig.https)

        code match {

          // First stage of request. User is redirected to an external URL, where they
          // authorize our app. The external provider then sends us back to this handler
          // with a code parameter, initiating the second phase.
          case None =>
            val state = UUID.randomUUID().toString
            Cache.set(sessionId, state, 30 * 60)
            val redirectUrl = provider.buildRedirectUrl(handlerUrl, state)
            Logger.debug(s"OAuth2 redirect URL: $redirectUrl")
            immediate(Redirect(redirectUrl).withSession(request.session + (SessionKey -> sessionId)))

          // Second phase of request. Using our new code, and with the same random session
          // nonce, proceed to get an access token, the user data, and handle the account
          // creation or updating.
          case Some(c) => if (checkSessionNonce(sessionId, state)) {
            Cache.remove(sessionId)
            (for {
              info <- oAuth2Flow.getAccessToken(provider, handlerUrl, c)
              userData <- oAuth2Flow.getUserData(provider, info)
              account <- getOrCreateAccount(provider, userData)
              authRequest = OAuth2Request(Right(account), request)
              result <- block(authRequest)
            } yield result) recoverWith {
              case e@AuthenticationError(msg) =>
                Logger.error(msg)
                block(OAuth2Request(Left(Messages("login.error.oauth2.info",
                  provider.name.toUpperCase)), request))
            }
          } else immediate(BadRequest("Invalid session ID"))
        }
      } getOrElse {
        notFoundError(request)
      }
   }
  }
}