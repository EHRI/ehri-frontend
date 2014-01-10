package controllers.core.auth.oauth2

import models._
import play.api.mvc._
import backend.{ApiUser, Backend}
import play.api.Logger
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import play.api.cache.Cache
import java.util.UUID
import java.net.URLEncoder
import play.api.Play._
import play.api.mvc.SimpleResult
import play.api.mvc.Call
import models.sql.{SqlAccount, OAuth2Association}
import play.api.db.DB

/**
 * Oauth2 login handler implementation, cribbed extensively
 * from SecureSocial.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Oauth2LoginHandler {

  self: Controller =>

  val backend: Backend

  val userDAO: AccountDAO

  val SSLEnabled = current.configuration.getBoolean("securesocial.ssl").getOrElse(true)
  val SessionKey = "sid"

  private def getAccessToken[A](provider: OAuth2Provider, handler: Call, code: String)(implicit request: Request[A]):Future[OAuth2Info] = {
    val params = Map(
      OAuth2Constants.ClientId -> Seq(provider.settings.clientId),
      OAuth2Constants.ClientSecret -> Seq(provider.settings.clientSecret),
      OAuth2Constants.GrantType -> Seq(OAuth2Constants.AuthorizationCode),
      OAuth2Constants.Code -> Seq(code),
      OAuth2Constants.RedirectUri -> Seq(handler.absoluteURL(SSLEnabled))
    )
    WS.url(provider.settings.accessTokenUrl).post(params).map(provider.buildOAuth2Info)
  }

  private def getUserData[A](provider: OAuth2Provider, info: OAuth2Info)(implicit request: Request[A]):Future[UserData] = {
    WS.url(provider.settings.userInfoUrl + info.accessToken).get()
      .map(provider.getUserData)
  }

  private def getOrCreateAccount(provider: OAuth2Provider, userData: UserData): Future[Account] = {
    val profileData = Map(
      UserProfileF.NAME -> userData.name,
      UserProfileF.IMAGE_URL -> userData.imageUrl
    )
    OAuth2Association.findByProviderInfo(userData.providerId, provider.name).flatMap(_.user).map { account =>
      Logger.info(s"Found existing association for $userData/${provider.name}")
      immediate(account)
    } getOrElse{
      // User has an account already, so try and find them by email. If so, add an association...
      userDAO.findByEmail(userData.email).map { account =>
        Logger.info(s"Creating new association for $userData/${provider.name}")
        OAuth2Association.addAssociation(account, userData.providerId, provider.name)
        immediate(account)
      } getOrElse {
        Logger.info(s"Creating new account for $userData/${provider.name}")
        // Create a new account!
        implicit val apiUser = ApiUser(Some("admin"))
        backend.createNewUserProfile(profileData).map { userProfile =>
          val account = userDAO.create(userProfile.id, userData.email.toLowerCase, verified = true, staff = false)
          OAuth2Association.addAssociation(account, userData.providerId, provider.name)
          account
        }
      }
    }
  }

  def buildRequestParams[A](provider: OAuth2Provider, handler: Call, state: String)(implicit request: Request[A]): Seq[(String,String)] = {
    Seq(
      OAuth2Constants.ClientId -> provider.settings.clientId,
      OAuth2Constants.RedirectUri -> handler.absoluteURL(SSLEnabled), //
      OAuth2Constants.ResponseType -> OAuth2Constants.Code,
      OAuth2Constants.State -> state,
      OAuth2Constants.Scope -> provider.settings.scope
    )
  }

  object oauth2LoginPostAction {
    def async(provider: OAuth2Provider, handler: Call)(f: Account => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      Action.async { implicit request =>
        val sessionId = request.session.get(SessionKey).getOrElse(UUID.randomUUID().toString)

        request.getQueryString(OAuth2Constants.Code) match {
          // First stage of request...
          case None => {
            val state = UUID.randomUUID().toString
            Cache.set(sessionId, state)

            val params: Seq[(String, String)] = buildRequestParams(provider, handler, state)
            val url = provider.settings.authorizationUrl +
              params.map( p => p._1 + "=" + URLEncoder.encode(p._2, "UTF-8")).mkString("?", "&", "")
            Logger.debug(url)
            immediate(Redirect(url).withSession(request.session + (SessionKey, sessionId)))
          }

          case Some(code) => {
            val newStateOpt = request.getQueryString(OAuth2Constants.State)
            (for {
              // check if the state we sent is equal to the one we're receiving now before continuing the flow.
              originalState <- Cache.getAs[String](sessionId)
              currentState <- newStateOpt if originalState == currentState
            } yield {
              Cache.remove(sessionId)

              for {
                info <- getAccessToken(provider, handler, code)
                userData <- getUserData(provider, info)
                account <- getOrCreateAccount(provider, userData)
                result <- f(account)(request)
              } yield result

            }).getOrElse {
              // Session key or states didn't match - throw an error
              Logger.error("OAuth2 state mismatch!")
              Logger.debug("Session id: " + sessionId)
              Logger.debug("Orig state: " + Cache.getAs[String](sessionId))
              Logger.debug("New state:  " + newStateOpt)
              throw new OAuth2Error("Invalid session keys")
            }
          }
        }
      }
    }

    def apply(provider: OAuth2Provider, handler: Call)(f: Account => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(provider, handler)(f.andThen(_.andThen(t => immediate(t))))
    }
  }
}