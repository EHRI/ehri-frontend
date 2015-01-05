package controllers.portal

import backend.{Backend, IdGenerator}
import com.google.inject.{Inject, Singleton}
import controllers.base.SessionPreferences
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import defines.EntityType
import models.{Group, AccountDAO, Concept, Vocabulary}
import play.api.libs.concurrent.Execution.Implicits._
import solr.SolrConstants
import utils.SessionPrefs
import utils.search._
import views.html.p

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Groups @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                                  userDAO: AccountDAO)
  extends PortalController
  with Generic[Group]
  with Search
  with FacetConfig
  with SessionPreferences[SessionPrefs] {

  val defaultPreferences = new SessionPrefs

  private val portalGroupRoutes = controllers.portal.routes.Groups

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
    Ok(p.group.show(request.item))
  }
}
