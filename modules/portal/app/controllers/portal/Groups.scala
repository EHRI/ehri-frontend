package controllers.portal

import auth.AccountManager
import backend.DataApi
import backend.rest.cypher.Cypher
import com.google.inject.{Inject, Singleton}
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.Group
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import utils.MovedPageLookup
import utils.search._
import views.MarkdownRenderer

@Singleton
case class Groups @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends PortalController
  with Generic[Group]
  with Search
  with FacetConfig {

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
    Ok(views.html.group.show(request.item))
  }
}
