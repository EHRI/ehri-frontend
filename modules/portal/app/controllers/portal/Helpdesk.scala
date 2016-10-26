package controllers.portal

import javax.inject._

import backend.{BadHelpdeskResponse, HelpdeskService}
import controllers.Components
import controllers.portal.base.PortalController
import models.{Repository, UserProfile}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.RequestHeader


@Singleton
case class Helpdesk @Inject()(
  components: Components,
  mailer: MailerClient,
  helpdeskService: HelpdeskService
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
    helpdeskService.available.flatMap { repos =>
      userDataApi.fetch[Repository](repos.map(_._1)).map { institutions =>
        Ok(views.html.helpdesk.helpdesk(prefilledForm, institutions))
      }
    }
  }

  private def sendMessageEmail(emailAddress: String, query: String)(implicit request: RequestHeader) {
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
      errorForm =>helpdeskService.available.flatMap { repos =>
        userDataApi.fetch[Repository](repos.map(_._1)).map { institutions =>
          BadRequest(views.html.helpdesk.helpdesk(errorForm, institutions))
        }
      },
      data => {
        val (email, query, copyMe) = data
        if (email.isEmpty && copyMe) {
          helpdeskService.available.flatMap { repos =>
            userDataApi.fetch[Repository](repos.map(_._1)).map { institutions =>
              BadRequest(views.html.helpdesk.helpdesk(
                boundForm
                  .withError("email", "error.required"), institutions))
            }
          }
        } else {
          if (copyMe && email.isDefined) {
            sendMessageEmail(email.get, query)
          }

          helpdeskService.askQuery(query).flatMap { responses =>
            userDataApi.fetch[Repository](responses.map(_._1)).map { institutions =>
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
