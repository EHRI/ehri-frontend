package controllers.portal

import auth.AccountManager
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.Link
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import utils.search._
import views.MarkdownRenderer

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Links @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: utils.MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer
) extends PortalController
  with Generic[Link]
  with Search
  with FacetConfig {

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
    Ok(views.html.link.show(request.item))
  }
}
