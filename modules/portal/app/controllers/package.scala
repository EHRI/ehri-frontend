import play.api.i18n.{Lang, Messages}
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import views.html.layout.errorLayout

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
package object controllers {

  def isAjax(implicit request: RequestHeader, globalConfig: global.GlobalConfig): Boolean =
    request.headers.get("X-REQUESTED-WITH").exists(_.toUpperCase == "XMLHTTPREQUEST")

  def renderError(titleKey: String, body: Html)(implicit request: RequestHeader, globalConfig: global.GlobalConfig, lang: Lang): Html =
    if (isAjax(request, globalConfig)) body else errorLayout(Messages(titleKey))(body)
}
