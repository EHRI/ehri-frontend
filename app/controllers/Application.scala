package controllers

import play.api.mvc._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.Play.current
import base.{Authorizer,AuthController,LoginHandler}
import play.api.data.Form
import play.api.data.Forms._
import models.sql.OpenIDUser
import play.api.i18n.Messages
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.concurrent.Execution.Implicits._
import models.base.{DescribedEntity, AccessibleEntity}
import solr.facet.FacetData


object Application extends Controller with Auth with LoginLogout with Authorizer with AuthController {

  lazy val loginHandler: LoginHandler = current.plugin(classOf[LoginHandler]).get

  /**
   * Look up the 'show' page of a generic item id
   * @param id
   */
  def genericShow(id: String) = Action {
    NotImplemented
  }


  def index = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      Ok(views.html.index("Your new application is ready."))
    }
  }

  val emailForm = Form(single("email" -> email))

  def login = loginHandler.login
  def loginPost = loginHandler.loginPost
  def logout = loginHandler.logout

  // Testing search
  def search = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      import solr._
      val sp = SearchParams.form.bindFromRequest.value.get
      val facets = FacetData.bindFromRequest(FacetData.facets)
      AsyncRest {
        SolrDispatcher(userOpt).list(sp, facets, FacetData.facets).map { resOrErr =>
          resOrErr.right.map { res =>
            AsyncRest {
              rest.SearchDAO(userOpt).list(res.items.map(_.id)).map { listOrErr =>
                listOrErr.right.map { list =>
                  Ok(views.html.search.search(res.copy(items = list),
                    sp, facets, routes.Application.search))
                }
              }
            }
          }
        }
      }
    }
  }
}