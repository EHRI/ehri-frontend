//
// Global request object
//

import play.api._
import play.api.mvc._

import org.apache.commons.codec.binary.Base64

import play.api.Play.current
import play.filters.csrf.CSRFFilter
import rest.EntityDAO
import solr.SolrIndexer
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

    import play.api.libs.concurrent.Execution.Implicits._

    // Bind the EntityDAO Create/Update/Delete actions
    // to the SolrIndexer update/delete handlers
    EntityDAO.addCreateHandler { item =>
      Logger.logger.info("Binding creation event to Solr create action")
      solr.SolrIndexer.updateItems(Stream(item)).map { batchList =>
        batchList.map { r => r match {
            case e: SolrErrorResponse => Logger.logger.error("Solr update error: " + e.err)
            case ok => ok
          }
        }
      }
    }

    EntityDAO.addUpdateHandler { item =>
      Logger.logger.info("Binding update event to Solr update action")
      solr.SolrIndexer.updateItems(Stream(item)).map { batchList =>
        batchList.map { r => r match {
            case e: SolrErrorResponse => Logger.logger.error("Solr update error: " + e.err)
            case ok => ok
          }
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