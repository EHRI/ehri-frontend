package controllers.portal

import auth.AccountManager
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.RequestHeader
import backend.{BadHelpdeskResponse, HelpdeskDAO, Backend}
import models.{UserProfile, Repository}
import javax.inject._
import backend.rest.SearchDAO
import controllers.portal.base.PortalController
import utils.MovedPageLookup
import utils.search.SearchItemResolver

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Helpdesk @Inject()(
  implicit  app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  backend: Backend,
  resolver: SearchItemResolver,
  accounts: AccountManager,
  mailer: MailerClient,
  helpdeskDAO: HelpdeskDAO,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  search: SearchDAO
) extends PortalController {

  import play.api.data.Form
  import play.api.data.Forms._

  private val helpdeskForm = Form(
    tuple(
      "email" -> optional(email),
      "query" -> nonEmptyText,
      "copyMe" -> default(boolean, false)
    )
  )

  def prefilledForm(implicit userOpt: Option[UserProfile]): Form[(Option[String],String,Boolean)] =
    helpdeskForm.fill(
      (userOpt.flatMap(_.account).map(_.email), "", false)).discardingErrors

  def helpdesk = OptionalUserAction.async { implicit request =>
    helpdeskDAO.available.flatMap { repos =>
      search.list[Repository](repos.map(_._1)).map { institutions =>
        Ok(views.html.helpdesk.helpdesk(prefilledForm, institutions))
      }
    }
  }

  private def sendMessageEmail(emailAddress: String, query: String)(implicit request: RequestHeader): Unit = {
    val email = Email(
      subject = "EHRI Portal Helpdesk",
      to = Seq(emailAddress),
      from = "EHRI User <noreply@ehri-project.eu>",
      bodyText = Some(query),
      bodyHtml = Some(query)
    )
    mailer.send(email)
  }

  def helpdeskPost = OptionalUserAction.async { implicit request =>
    val boundForm = helpdeskForm.bindFromRequest
    boundForm.fold(
      errorForm =>helpdeskDAO.available.flatMap { repos =>
        search.list[Repository](repos.map(_._1)).map { institutions =>
          BadRequest(views.html.helpdesk.helpdesk(errorForm, institutions))
        }
      },
      data => {
        val (email, query, copyMe) = data
        if (email.isEmpty && copyMe) {
          helpdeskDAO.available.flatMap { repos =>
            search.list[Repository](repos.map(_._1)).map { institutions =>
              BadRequest(views.html.helpdesk.helpdesk(
                boundForm
                  .withError("email", "error.required"), institutions))
            }
          }
        } else {
          if (copyMe && email.isDefined) {
            sendMessageEmail(email.get, query)
          }

          helpdeskDAO.askQuery(query).flatMap { responses =>
            search.list[Repository](responses.map(_._1)).map { institutions =>
              Ok(views.html.helpdesk.results(prefilledForm.fill(data), query, responses, institutions))
            }
          } recover {
            case e: BadHelpdeskResponse =>
              InternalServerError(views.html.helpdesk.error(e))
          }
        }
      }
    )
  }
}
