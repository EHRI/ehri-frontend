//
// Global request object
//

import defines.EntityType
import global.RouteRegistry
import play.api._
import play.api.mvc._

import org.apache.commons.codec.binary.Base64

import play.api.Play.current
import play.filters.csrf.CSRFFilter
import rest.EntityDAO
import solr.SolrIndexer.SolrErrorResponse


/**
 * Filter that applies CSRF protection unless a particular
 * custom header is present. The value of the header is
 * not checked.
 */
class AjaxCSRFFilter extends EssentialFilter {
  var csrfFilter = new CSRFFilter()

  val AJAX_HEADER_TOKEN = "ajax-ignore-csrf"

  def apply(next: EssentialAction) = new EssentialAction {
    def apply(request: RequestHeader) = {
      if (request.headers.keys.contains(AJAX_HEADER_TOKEN))
        next(request)
      else
        csrfFilter(next)(request)
    }
  }
}


object Global extends WithFilters(new AjaxCSRFFilter()) with GlobalSettings {
    
  override def onStart(app: Application) {

    // Hack for bug #845
    app.routes

    // Register JSON models!
    models.json.Utils.registerModels

    RouteRegistry.setUrl(EntityType.SystemEvent, controllers.core.routes.SystemEvents.get _)
    RouteRegistry.setUrl(EntityType.DocumentaryUnit, controllers.archdesc.routes.DocumentaryUnits.get _)
    RouteRegistry.setUrl(EntityType.HistoricalAgent, controllers.authorities.routes.HistoricalAgents.get _)
    RouteRegistry.setUrl(EntityType.Repository, controllers.archdesc.routes.Repositories.get _)
    RouteRegistry.setUrl(EntityType.Group, controllers.core.routes.Groups.get _)
    RouteRegistry.setUrl(EntityType.UserProfile, controllers.core.routes.UserProfiles.get _)
    RouteRegistry.setUrl(EntityType.Annotation, controllers.core.routes.Annotations.get _)
    RouteRegistry.setUrl(EntityType.Link, controllers.core.routes.Links.get _)
    RouteRegistry.setUrl(EntityType.Vocabulary, controllers.vocabs.routes.Vocabularies.get _)
    RouteRegistry.setUrl(EntityType.AuthoritativeSet, controllers.authorities.routes.AuthoritativeSets.get _)
    RouteRegistry.setUrl(EntityType.Concept, controllers.vocabs.routes.Concepts.get _)
    RouteRegistry.setUrl(EntityType.Country, controllers.archdesc.routes.Countries.get _)

    // Register menu parts - MASSIVE HACK to put this here!!!
    Logger.logger.info("Configuring menu... " + controllers.vocabs.routes.Concepts.search.url)
    global.MainMenuConfig.putMain(
      "pages.search", controllers.routes.Search.search.url)
    global.MainMenuConfig.putMain(
      "contentTypes.documentaryUnit", controllers.archdesc.routes.DocumentaryUnits.search.url)
    global.MainMenuConfig.putMain(
      "contentTypes.historicalAgent", controllers.authorities.routes.HistoricalAgents.search.url)
    global.MainMenuConfig.putMain(
      "contentTypes.repository", controllers.archdesc.routes.Repositories.search.url)
    global.MainMenuConfig.putMain(
      "contentTypes.cvocConcept", controllers.vocabs.routes.Concepts.search.url)

    Logger.logger.info("Configuring admin menu...")
    global.MainMenuConfig.putAdmin(
      "contentTypes.userProfile", controllers.core.routes.UserProfiles.search.url)
    global.MainMenuConfig.putAdmin(
      "contentTypes.group", controllers.core.routes.Groups.list.url)
    global.MainMenuConfig.putAdmin(
      "contentTypes.country", controllers.archdesc.routes.Countries.search.url)
    global.MainMenuConfig.putAdmin(
      "contentTypes.cvocVocabulary", controllers.vocabs.routes.Vocabularies.list.url)
    global.MainMenuConfig.putAdmin(
      "contentTypes.authoritativeSet", controllers.authorities.routes.AuthoritativeSets.list.url)
    global.MainMenuConfig.putAdmin("s1", "-")
    global.MainMenuConfig.putAdmin(
      "contentTypes.systemEvent", controllers.core.routes.SystemEvents.list.url)
    global.MainMenuConfig.putAdmin("s2", "-")
    global.MainMenuConfig.putAdmin(
      "search.updateIndex", controllers.routes.Search.updateIndex.url
    )

    import play.api.libs.concurrent.Execution.Implicits._

    // Bind the EntityDAO Create/Update/Delete actions
    // to the SolrIndexer update/delete handlers
    EntityDAO.addCreateHandler { item =>
      Logger.logger.info("Binding creation event to Solr create action")
        solr.SolrIndexer.updateItem(item, commit = true).map { r => r match {
          case e: SolrErrorResponse => Logger.logger.error("Solr update error: " + e.err)
          case ok => ok
        }
      }
    }

    EntityDAO.addUpdateHandler { item =>
      Logger.logger.info("Binding update event to Solr update action")
        solr.SolrIndexer.updateItem(item, commit = true).map { r => r match {
          case e: SolrErrorResponse => Logger.logger.error("Solr update error: " + e.err)
          case ok => ok
        }
      }
    }

    EntityDAO.addDeleteHandler { item =>
      Logger.logger.info("Binding delete event to Solr delete action")
      solr.SolrIndexer.deleteItemsById(Stream(item)).map { r => r match {
          case e: SolrErrorResponse => Logger.logger.error("Solr update error: " + e.err)
          case ok => ok
        }
      }
    }
  }

  private def noAuthAction = Action { request =>
    play.api.mvc.Results.Unauthorized("This application required authentication")
      .withHeaders("WWW-Authenticate" -> "Basic")
  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {

    val usernameOpt = current.configuration.getString("auth.basic.username")
    val passwordOpt = current.configuration.getString("auth.basic.password")
    if (usernameOpt.isDefined && passwordOpt.isDefined) {
      for {
        username <- usernameOpt
        password <- passwordOpt
        authstr <- request.headers.get("Authorization")
        base64 <- authstr.split(" ").drop(1).headOption
        authvals = new String(Base64.decodeBase64(base64.getBytes))
      } yield {
        authvals.split(":").toList match {
          case u :: p :: Nil if u == username && p == password => super.onRouteRequest(request)
          case _ => Some(noAuthAction)
        }
      }.getOrElse(noAuthAction)

    } else {
      super.onRouteRequest(request)
    }
  }
}