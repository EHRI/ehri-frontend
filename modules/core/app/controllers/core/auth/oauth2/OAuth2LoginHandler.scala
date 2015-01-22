package controllers.core.auth.oauth2

import auth.AuthenticationError
import auth.oauth2.providers.OAuth2Provider
import auth.oauth2.{OAuth2Constants, UserData, OAuth2Info}
import controllers.base.AuthController
import global.GlobalConfig
import models._
import play.api.i18n.Messages
import play.api.mvc._
import backend.{AuthenticatedUser, AnonymousUser, Backend}
import play.api.Logger
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import play.api.cache.Cache
import java.util.UUID
import play.api.Play._
import play.api.mvc.Result
import play.api.mvc.Call
import play.api.libs.json.{JsString, Json}
import controllers.core.auth.AccountHelpers

/**
 * Oauth2 login handler implementation, cribbed extensively
 * from SecureSocial.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait OAuth2LoginHandler extends AccountHelpers {

  self: Controller with AuthController =>

  val backend: Backend
  val accounts: auth.AccountManager
  val globalConfig: GlobalConfig

  private val SessionKey = "sid"

  case class OAuth2Request[A](
    accountOrErr: Either[String, Account],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  private def getAccessToken[A](provider: OAuth2Provider, handler: Call, code: String)(implicit request: Request[A]):Future[OAuth2Info] = {
    Logger.debug(s"Fetching access token at ${provider.settings.accessTokenUrl}")
    WS.url(provider.getAccessTokenUrl)
      .withHeaders(provider.getAccessTokenHeaders: _*)
      .post(provider.getAccessTokenParams(code, handler.absoluteURL(globalConfig.https)))
      .map { r =>
      println(s"ACCESS DATA FOR PROVIDER: ${provider.name}----\n${r.body}\n------")
      provider.buildOAuth2Info(r.body).getOrElse {
        throw new AuthenticationError(s"Unable to fetch access token and info for provider ${provider.name} " +
          s" via response data: ${r.body}")
      }
    }
  }

  private def getUserData[A](provider: OAuth2Provider, info: OAuth2Info)(implicit request: Request[A]): Future[UserData] = {
    val url: String = provider.getUserInfoUrl(info)
    val headers: Seq[(String, String)] = provider.getUserInfoHeader(info)
    Logger.debug(s"Fetching info at $url with headers $headers")
    WS.url(url)
      .withHeaders(headers: _*).get()
      .map { r =>
      println(s"USER DATA FOR PROVIDER: ${provider.name}----\n${r.body}\n------")
      provider.getUserData(r.body).getOrElse{
        throw new AuthenticationError(s"Unable to fetch user info for provider ${provider.name} " +
          s" via response data: ${r.body}")
      }
    }
  }

  private def updateUserInfo(account: Account, userData: UserData): Future[UserProfile] = {
    implicit val apiUser = AuthenticatedUser(account.id)
    backend.get[UserProfile](account.id).flatMap { up =>
      backend.patch[UserProfile](account.id, Json.obj(
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
      profile <- backend.createNewUserProfile[UserProfile](profileData, groups = defaultPortalGroups)
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

  private def checkSessionNonce[A](sessionId: String)(implicit request: Request[A]): Boolean = {
    val newStateOpt = request.getQueryString(OAuth2Constants.State)
    val origStateOpt: Option[String] = Cache.getAs[String](sessionId)
    (for {
    // check if the state we sent is equal to the one we're receiving now before continuing the flow.
      originalState <- origStateOpt
      currentState <- newStateOpt
    } yield {
      val check = originalState == currentState
      if (!check) Logger.error(s"OAuth2 state mismatch: sessionId: $sessionId, " +
        s"original token: $origStateOpt, new token: $newStateOpt")
      check
    }).getOrElse(false)
  }

  def OAuth2LoginAction(provider: OAuth2Provider, handler: Call) = new ActionBuilder[OAuth2Request] {
    override def invokeBlock[A](request: Request[A], block: (OAuth2Request[A]) => Future[Result]): Future[Result] = {
      implicit val r = request

      // Create a random nonce to stamp this OAuth2 session
      val sessionId = request.session.get(SessionKey).getOrElse(UUID.randomUUID().toString)

      request.getQueryString(OAuth2Constants.Code) match {

        // First stage of request. User is redirected to an external URL, where they
        // authorize our app. The external provider then sends us back to this handler
        // with a code parameter, initiating the second phase.
        case None =>
          val state = UUID.randomUUID().toString
          Cache.set(sessionId, state, 30 * 60)
          val redirectUrl = provider.buildRedirectUrl(handler.absoluteURL(globalConfig.https), state)
          Logger.debug(s"OAuth2 redirect URL: $redirectUrl")
          immediate(Redirect(redirectUrl).withSession(request.session + (SessionKey -> sessionId)))

        // Second phase of request. Using our new code, and with the same random session
        // nonce, proceed to get an access token, the user data, and handle the account
        // creation or updating.
        case Some(code) => if (checkSessionNonce(sessionId)) {
          Cache.remove(sessionId)
          (for {
            info <- getAccessToken(provider, handler, code)
            userData <- getUserData(provider, info)
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
    }
  }
}