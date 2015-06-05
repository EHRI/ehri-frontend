package controllers.portal

import auth.AccountManager
import backend.rest.cypher.Cypher
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Result, RequestHeader}
import utils.MovedPageLookup
import views.MarkdownRenderer
import scala.concurrent.Future.{successful => immediate}
import backend.{Backend, FeedbackDAO}
import javax.inject._
import com.typesafe.plugin.MailerAPI
import controllers.portal.base.PortalController

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Feedback @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  feedbackDAO: FeedbackDAO,
  backend: Backend,
  accounts: AccountManager,
  mailer: MailerAPI,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends PortalController {

  import utils.forms._
  import play.api.data.Form
  import play.api.data.Forms._
  import utils.forms.HoneyPotForm._
  import utils.forms.TimeCheckForm._

  case class CheckFeedbackData(
    timestamp: String,
    blankCheck: String
  ) extends HoneyPotForm with TimeCheckForm

  private def checkFbForm(implicit app: play.api.Application) = Form(
    mapping(
      TIMESTAMP -> nonEmptyText,
      BLANK_CHECK -> text.verifying(s => s.isEmpty)
    )(CheckFeedbackData.apply)(CheckFeedbackData.unapply)
      verifying blankFieldIsBlank verifying formSubmissionTime
  )

  def feedback = OptionalUserAction { implicit request =>
    Ok(views.html.feedback(models.Feedback.form))
  }

  private def getCopyMail(feedbackType: Option[models.Feedback.Type.Value])(implicit app: play.api.Application): Seq[String] = {
    import scala.collection.JavaConverters._
    val defaultOpt = app.configuration.getStringList("ehri.portal.feedback.copyTo").map(_.asScala)
    ((for {
      ft <- feedbackType
      ct <- app.configuration.getStringList(s"ehri.portal.feedback.$ft.copyTo").map(_.asScala)
    } yield ct) orElse defaultOpt).getOrElse(Seq.empty)
  }

  private def sendMessageEmail(feedback: models.Feedback)(implicit app: play.api.Application, request: RequestHeader): Unit = {
    for {
      accTo <- getCopyMail(feedback.`type`)
    } yield {
      val text = feedback.text.map { msg =>
        s"""
          |From: ${feedback.name.getOrElse("Anonymous")} (${feedback.email.getOrElse("no email given")})
          |
          |$msg
        """.stripMargin
      }.getOrElse("No message provided")
      mailer
        .setSubject("EHRI Portal Feedback" + feedback.name.map(n => s" from $n").getOrElse(""))
        .setRecipient(accTo)
        .setReplyTo(feedback.email.getOrElse("noreply@ehri-project.eu"))
        .setFrom("EHRI User <noreply@ehri-project.eu>")
        .send(text, markdown.renderMarkdown(text))
    }
  }

  def feedbackPost = OptionalUserAction.async { implicit request =>
    val boundForm: Form[models.Feedback] = models.Feedback.form.bindFromRequest()

    def response(f: Form[models.Feedback]): Result =
      if (isAjax) BadRequest(f.errorsAsJson) else BadRequest(views.html.feedback(f))

    // check the anti-bot measures and immediately return the original
    // form. No feedback needed since they're (hopefully) a bot.
    if (checkFbForm.bindFromRequest.hasErrors) immediate(response(boundForm))
    else boundForm.fold(
      errorForm => immediate(response(errorForm)),
      feedback => {
        val moreFeedback = request.userOpt.map { user =>
          feedback.copy(userId = Some(user.id), name = Some(feedback.name.getOrElse(user.model.name)))
            .copy(email = feedback.email.orElse(user.account.map(_.email)))
        }.getOrElse(feedback)
          .copy(context = Some(models.FeedbackContext.fromRequest),
            mode = Some(app.mode))
        feedbackDAO.create(moreFeedback).map { id =>
          sendMessageEmail(moreFeedback)
          if (isAjax) Ok(id)
          else Redirect(controllers.portal.routes.Portal.index())
            .flashing("success" -> "feedback.thanks.message")
        }
      }
    )
  }

  def list = AdminAction.async { implicit request =>
    feedbackDAO.list("order" -> "-createdAt").map { flist =>
      Ok(views.html.feedbackList(flist.filter(_.text.isDefined)))
    }
  }
}
