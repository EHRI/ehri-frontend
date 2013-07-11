//
// Global request object
//

import defines.EntityType
import play.api._
import play.api.libs.json.{Format, Reads}
import play.api.mvc._

import org.apache.commons.codec.binary.Base64

import play.api.Play.current
import play.filters.csrf.CSRFFilter
import rest.EntityDAO
import scala.Some
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


// Note: this is in the default package.
object Global extends WithFilters(new AjaxCSRFFilter()) with GlobalSettings {
    
  override def onStart(app: Application) {

    // Register JSON models!
    models.json.Utils.registerModels

    // Register menu parts
    views.MenuConfig.putMain(
      "pages.search", controllers.routes.Search.search.url)
    views.MenuConfig.putMain(
      "contentTypes.documentaryUnit", controllers.routes.DocumentaryUnits.search.url)
    views.MenuConfig.putMain(
      "contentTypes.historicalAgent", controllers.routes.HistoricalAgents.search.url)
    views.MenuConfig.putMain(
      "contentTypes.repository", controllers.routes.Repositories.search.url)
    views.MenuConfig.putMain(
      "contentTypes.cvocConcept", controllers.routes.Concepts.search.url)

    views.MenuConfig.putAdmin(
      "contentTypes.userProfile", controllers.routes.UserProfiles.search.url)
    views.MenuConfig.putAdmin(
      "contentTypes.group", controllers.routes.Groups.list.url)
    views.MenuConfig.putAdmin(
      "contentTypes.country", controllers.routes.Countries.search.url)
    views.MenuConfig.putAdmin(
      "contentTypes.cvocVocabulary", controllers.routes.Vocabularies.list.url)
    views.MenuConfig.putAdmin(
      "contentTypes.authoritativeSet", controllers.routes.AuthoritativeSets.list.url)
    views.MenuConfig.putAdmin(
      "contentTypes.systemEvent", controllers.routes.SystemEvents.list.url)

    import views.Helpers.RouteRegistry
    RouteRegistry.setUrl(EntityType.SystemEvent, controllers.routes.SystemEvents.get _)
    RouteRegistry.setUrl(EntityType.DocumentaryUnit, controllers.routes.DocumentaryUnits.get _)
    RouteRegistry.setUrl(EntityType.HistoricalAgent, controllers.routes.HistoricalAgents.get _)
    RouteRegistry.setUrl(EntityType.Repository, controllers.routes.Repositories.get _)
    RouteRegistry.setUrl(EntityType.Group, controllers.routes.Groups.get _)
    RouteRegistry.setUrl(EntityType.UserProfile, controllers.routes.UserProfiles.get _)
    RouteRegistry.setUrl(EntityType.Annotation, controllers.routes.Annotations.get _)
    RouteRegistry.setUrl(EntityType.Link, controllers.routes.Links.get _)
    RouteRegistry.setUrl(EntityType.Vocabulary, controllers.routes.Vocabularies.get _)
    RouteRegistry.setUrl(EntityType.AuthoritativeSet, controllers.routes.AuthoritativeSets.get _)
    RouteRegistry.setUrl(EntityType.Concept, controllers.routes.Concepts.get _)
    RouteRegistry.setUrl(EntityType.Country, controllers.routes.Countries.get _)


    import play.api.libs.concurrent.Execution.Implicits._

    // Bind the EntityDAO Create/Update/Delete actions
    // to the SolrIndexer update/delete handlers
    EntityDAO.addCreateHandler { item =>
      Logger.logger.info("Binding creation event to Solr create action")
      /*solr.SolrIndexer.updateItems(Stream(item)).map { batchList =>
        batchList.map { r => r match {
            case e: SolrErrorResponse => Logger.logger.error("Solr update error: " + e.err)
            case ok => ok
          }
        }
      }*/
    }

    EntityDAO.addUpdateHandler { item =>
      Logger.logger.info("Binding update event to Solr update action")
      /*solr.SolrIndexer.updateItems(Stream(item)).map { batchList =>
        batchList.map { r => r match {
            case e: SolrErrorResponse => Logger.logger.error("Solr update error: " + e.err)
            case ok => ok
          }
        }
      }*/
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