package controllers.portal

import backend.Backend
import com.google.inject.{Inject, Singleton}
import controllers.base.SessionPreferences
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import models.{Link, AccountDAO, Group}
import utils.SessionPrefs
import utils.search._
import views.html.p

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Links @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                                  userDAO: AccountDAO)
  extends PortalController
  with Generic[Link]
  with Search
  with FacetConfig {

  private val portalGroupRoutes = controllers.portal.routes.Links

  def browse(id: String) = GetItemAction(id).apply { implicit request =>
    Ok(p.link.show(request.item))
  }
}
