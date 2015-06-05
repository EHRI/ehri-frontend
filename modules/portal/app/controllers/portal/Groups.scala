package controllers.portal

import auth.AccountManager
import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.Group
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import utils.MovedPageLookup
import utils.search._
import views.MarkdownRenderer

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Groups @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer
) extends PortalController
  with Generic[Group]
  with Search
  with FacetConfig {

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
    Ok(views.html.group.show(request.item))
  }
}
