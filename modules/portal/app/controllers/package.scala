import global.GlobalConfig
import models.UserProfile
import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import views.html.layout.errorLayout

package object controllers {

  def isAjax(implicit request: RequestHeader): Boolean =
    request.headers.get("X-REQUESTED-WITH").exists(_.toUpperCase == "XMLHTTPREQUEST")

  def renderError(titleKey: String, body: Html)(implicit request: RequestHeader, globalConfig: GlobalConfig, messages: Messages, userOpt: Option[UserProfile] = None): Html =
    if (isAjax(request)) body else errorLayout(Messages(titleKey))(body)
}
