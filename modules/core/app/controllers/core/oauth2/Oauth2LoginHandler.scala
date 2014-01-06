package controllers.core.oauth2

import models.{Account, AccountDAO}
import play.api.mvc._
import backend.Backend
import play.api.Logger
import play.api.libs.ws.{WS, Response}
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import play.api.cache.Cache
import java.util.UUID
import java.net.URLEncoder
import play.api.Play._
import scala.Some
import play.api.mvc.SimpleResult
import play.api.mvc.Call
import play.api.libs.json.Json


case class UserData(
  email: String,
  name: String,
  imageUrl: String
)

case class Settings(
  clientId: String,
  clientSecret: String,
  ssl: Boolean
)

/**
 * Pinched from SecureSocial
 */
object OAuth2Settings {
  val AuthorizationUrl = "authorizationUrl"
  val AccessTokenUrl = "accessTokenUrl"
  val ClientId = "clientId"
  val ClientSecret = "clientSecret"
  val Scope = "scope"
}

object OAuth2Constants {
  val ClientId = "client_id"
  val ClientSecret = "client_secret"
  val RedirectUri = "redirect_uri"
  val Scope = "scope"
  val ResponseType = "response_type"
  val State = "state"
  val GrantType = "grant_type"
  val AuthorizationCode = "authorization_code"
  val AccessToken = "access_token"
  val Error = "error"
  val Code = "code"
  val TokenType = "token_type"
  val ExpiresIn = "expires_in"
  val RefreshToken = "refresh_token"
  val AccessDenied = "access_denied"
}

case class OAuth2Settings(
  authorizationUrl: String,
  accessTokenUrl: String,
  clientId: String,
  clientSecret: String,
  scope: Option[String]
)

case class OAuth2Info(
  accessToken: String,
  tokenType: Option[String] = None,
  expiresIn: Option[Int] = None,
  refreshToken: Option[String] = None
)

trait OAuth2Provider {
  def buildOAuth2Info(response: Response): OAuth2Info = {
    val json = response.json
    if ( Logger.isDebugEnabled ) {
      Logger.debug("[securesocial] got json back [" + Json.prettyPrint(json) + "]")
    }
    OAuth2Info(
      (json \ OAuth2Constants.AccessToken).as[String],
      (json \ OAuth2Constants.TokenType).asOpt[String],
      (json \ OAuth2Constants.ExpiresIn).asOpt[Int],
      (json \ OAuth2Constants.RefreshToken).asOpt[String]
    )
  }

  def getUserData(response: Response): UserData
  def settings: Settings
  val authorizationUrl: String
  val accessTokenUrl: String
  val oauth2Scopes: String
  val userInfoUrl: String
}


/**
 * Oauth2 login handler implementation.
 */
trait Oauth2LoginHandler {

  self: Controller =>

  val backend: Backend

  private lazy val userDAO: AccountDAO = play.api.Play.current.plugin(classOf[AccountDAO]).get

  private def getAccessToken[A](provider: OAuth2Provider, handler: Call, code: String)(implicit request: Request[A]):Future[OAuth2Info] = {
    val params = Map(
      OAuth2Constants.ClientId -> Seq(provider.settings.clientId),
      OAuth2Constants.ClientSecret -> Seq(provider.settings.clientSecret),
      OAuth2Constants.GrantType -> Seq(OAuth2Constants.AuthorizationCode),
      OAuth2Constants.Code -> Seq(code),
      OAuth2Constants.RedirectUri -> Seq(handler.absoluteURL(provider.settings.ssl))
    )
    WS.url(provider.accessTokenUrl).post(params).map(provider.buildOAuth2Info)
  }

  def buildRequestParams[A](provider: OAuth2Provider, handler: Call, state: String)(implicit request: Request[A]): Seq[(String,String)] = {
    Seq(
      OAuth2Constants.ClientId -> provider.settings.clientId,
      OAuth2Constants.RedirectUri -> handler.absoluteURL(provider.settings.ssl), //
      OAuth2Constants.ResponseType -> OAuth2Constants.Code,
      OAuth2Constants.State -> state,
      OAuth2Constants.Scope -> provider.oauth2Scopes
    )
  }

  object oauth2LoginPostAction {
    def async(provider: OAuth2Provider, handler: Call)(f: Account => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      Action.async { implicit request =>
        request.queryString.get(OAuth2Constants.Code).flatMap(_.headOption) match {
          // First stage of request...
          case None => {
            val state = UUID.randomUUID().toString
            val sessionId = request.session.get("sid").getOrElse(UUID.randomUUID().toString)
            Cache.set(sessionId, state)

            val params: Seq[(String, String)] = buildRequestParams(provider, handler, state)
            val url = provider.authorizationUrl +
              params.map( p => p._1 + "=" + URLEncoder.encode(p._2, "UTF-8")).mkString("?", "&", "")
            Logger.debug(url)
            immediate(Redirect(url).withSession(request.session + ("sid", sessionId)))
          }

          case Some(code) => {
            val user = for (
            // check if the state we sent is equal to the one we're receiving now before continuing the flow.
              sessionId <- request.session.get("sid") ;
              // todo: review this -> clustered environments
              originalState <- Cache.getAs[String](sessionId) ;
              currentState <- request.queryString.get(OAuth2Constants.State).flatMap(_.headOption) if originalState == currentState
            ) yield {
            }

            getAccessToken(provider, handler, code).flatMap { info =>
              val oauth2Info = Some(
                OAuth2Info(info.accessToken, info.tokenType, info.expiresIn, info.refreshToken)
              )
              Logger.info("" + oauth2Info)
              WS.url(provider.userInfoUrl).withQueryString(OAuth2Constants.AccessToken -> oauth2Info.get.accessToken).get().flatMap { response =>
              // Create account here!
                Logger.debug("USER DATA: " + Json.prettyPrint(response.json))
                val userData = provider.getUserData(response)
                userDAO.findByEmail(userData.email).map { account =>
                  f(account)(request)
                } getOrElse {
                  ???
                }
              }
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