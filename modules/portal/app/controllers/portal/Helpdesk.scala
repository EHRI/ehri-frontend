package controllers.portal

import auth.AccountManager
import play.api.mvc.RequestHeader
import scala.concurrent.Future.{successful => immediate}
import backend.{BadHelpdeskResponse, HelpdeskDAO, Backend}
import models.Repository
import com.google.inject._
import backend.rest.SearchDAO
import com.typesafe.plugin.MailerAPI
import controllers.portal.base.PortalController

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Helpdesk @Inject()(implicit helpdeskDAO: HelpdeskDAO, globalConfig: global.GlobalConfig, backend: Backend,
    accounts: AccountManager, mailer: MailerAPI)
  extends PortalController {

  import play.api.data.Form
  import play.api.data.Forms._

  private val helpdeskForm = Form(
    tuple(
      "email" -> email,
      "query" -> nonEmptyText,
      "copyMe" -> default(boolean, false)
    )
  )

  def helpdesk = OptionalUserAction { implicit request =>
    val prefilledData: (String,String, Boolean)
        = (request.userOpt.flatMap(_.account).map(_.email).getOrElse(""), "", false)
    val form = helpdeskForm.fill(prefilledData).discardingErrors
    Ok(views.html.p.helpdesk.helpdesk(form))
  }

  private def sendMessageEmail(email: String, query: String)(implicit request: RequestHeader): Unit = {
    mailer
      .setSubject("EHRI Portal Helpdesk")
      .setRecipient(email)
      .setFrom("EHRI User <noreply@ehri-project.eu>")
      .send(query, query)
  }

  def helpdeskPost = OptionalUserAction.async { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._
    helpdeskForm.bindFromRequest.fold(
      errorForm => immediate(BadRequest(views.html.p.helpdesk.helpdesk(errorForm))),
      data => {
        val (email, query, copyMe) = data
        if (copyMe) {
          sendMessageEmail(email, query)
        }

        helpdeskDAO.askQuery(query).flatMap { responses =>
          SearchDAO.list[Repository](responses.map(_.institutionId)).map { institutions =>
            Ok(views.html.p.helpdesk.results(email, query, responses, institutions))
          }
        } recover {
          case e: BadHelpdeskResponse =>
            InternalServerError(views.html.p.helpdesk.error(e))
        }
      }
    )
  }
}
