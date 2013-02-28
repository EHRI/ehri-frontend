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

// Note: this is in the default package.
object Global extends WithFilters(CSRFFilter()) with GlobalSettings {

  override def onStart(app: Application) {

    import play.api.libs.concurrent.Execution.Implicits._

    // Bind the EntityDAO Create/Update/Delete actions
    // to the SolrIndexer update/delete handlers
    EntityDAO.addCreateHandler { item =>
      SolrIndexer.updateItems(Stream(item)).map { batchList =>
        batchList.map { result =>
          result.left.map { err =>
            Logger.logger.error("Solr create error: " + err)
          }
        }
      }
    }

    EntityDAO.addUpdateHandler { item =>
      SolrIndexer.updateItems(Stream(item)).map { batchList =>
        batchList.map { result =>
          result.left.map { err =>
            Logger.logger.error("Solr update error: " + err)
          }
        }
      }
    }

    EntityDAO.addDeleteHandler { item =>
      SolrIndexer.deleteItemsById(Stream(item)).map { result =>
        result.left.map { err =>
          Logger.logger.error("Solr delete error: " + err)
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