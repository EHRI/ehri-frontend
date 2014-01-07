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

/**
 * Oauth2 login handler implementation.
 */
trait Oauth2LoginHandler {

  self: Controller =>

  val backend: Backend

  private lazy val userDAO: AccountDAO = play.api.Play.current.plugin(classOf[AccountDAO]).get

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
    WS.url(provider.settings.userInfoUrl)
      .withQueryString(OAuth2Constants.AccessToken -> info.accessToken)
      .get()
      .map(provider.getUserData)
  }

  private def getOrCreateAccount(userData: UserData): Future[Account] = {
    userDAO.findByEmail(userData.email).map { account =>
      immediate(account)
    } getOrElse {
      // Create a new account!
      implicit val apiUser = ApiUser(Some("admin"))
      for {
        user <- backend.createNewUserProfile
        updated = user.model.copy(name = userData.name)
        withData <- backend.update[UserProfile,UserProfileF](user.id, updated)
      } yield {
        userDAO.create(user.id, userData.email, staff = false) getOrElse {
          sys.error("Unable to create user account!")
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
        request.queryString.get(OAuth2Constants.Code).flatMap(_.headOption) match {
          // First stage of request...
          case None => {
            val state = UUID.randomUUID().toString
            val sessionId = request.session.get(SessionKey).getOrElse(UUID.randomUUID().toString)
            Cache.set(sessionId, state)

            val params: Seq[(String, String)] = buildRequestParams(provider, handler, state)
            val url = provider.settings.authorizationUrl +
              params.map( p => p._1 + "=" + URLEncoder.encode(p._2, "UTF-8")).mkString("?", "&", "")
            Logger.debug(url)
            immediate(Redirect(url).withSession(request.session + (SessionKey, sessionId)))
          }

          case Some(code) => {
            (for {
              // check if the state we sent is equal to the one we're receiving now before continuing the flow.
              sessionId <- request.session.get(SessionKey)
              originalState <- Cache.getAs[String](sessionId)
              currentState <- request.getQueryString(OAuth2Constants.State) if originalState == currentState
            } yield {

              for {
                info <- getAccessToken(provider, handler, code)
                userData <- getUserData(provider, info)
                account <- getOrCreateAccount(userData)
                result <- f(account)(request)
              } yield result

            }).getOrElse {
              // Session key or states didn't match - throw an error
              Logger.error("OAuth2 state mismatch: ")
              throw new OAuth2Error("Invalid session keys and/or")
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