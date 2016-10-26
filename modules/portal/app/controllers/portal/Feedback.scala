package controllers.portal

import auth.AccountManager
import backend.rest.cypher.Cypher
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{RequestHeader, Result}
import utils.{MovedPageLookup, PageParams}
import views.MarkdownRenderer

import scala.concurrent.Future.{successful => immediate}
import backend.{DataApi, FeedbackService}
import javax.inject._

import auth.handler.AuthHandler
import controllers.portal.base.PortalController

import scala.concurrent.ExecutionContext


@Singleton
case class Feedback @Inject()(
  implicit config: play.api.Configuration,
  app: play.api.Application, // FIXME: remove
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  authHandler: AuthHandler,   executionContext: ExecutionContext,   feedbackService: FeedbackService,
  dataApi: DataApi,
  accounts: AccountManager,
  mailer: MailerClient,
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

  private def getCopyMail(feedbackType: Option[models.Feedback.Type.Value])(implicit app: play.api.Application): Seq[String] = {
    import scala.collection.JavaConverters._
    val defaultOpt = config.getStringList("ehri.portal.feedback.copyTo").map(_.asScala)
    ((for {
      ft <- feedbackType
      ct <- config.getStringList(s"ehri.portal.feedback.$ft.copyTo").map(_.asScala)
    } yield ct) orElse defaultOpt).getOrElse(Seq.empty)
  }

  private def sendMessageEmail(feedback: models.Feedback)(implicit config: play.api.Configuration, request: RequestHeader): Unit = {
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
      val email = Email(
        subject = "EHRI Portal Feedback" + feedback.name.map(n => s" from $n").getOrElse(""),
        to = Seq(accTo),
        from = "EHRI User <noreply@ehri-project.eu>",
        replyTo = feedback.email,
        bodyText = Some(text),
        bodyHtml = Some(markdown.renderUntrustedMarkdown(text))
      )
      mailer.send(email)
    }
  }

  def feedback = OptionalUserAction { implicit request =>
    Ok(views.html.feedback.create(models.Feedback.form))
  }

  def feedbackPost = OptionalUserAction.async { implicit request =>
    val boundForm: Form[models.Feedback] = models.Feedback.form.bindFromRequest()

    def response(f: Form[models.Feedback]): Result =
      if (isAjax) BadRequest(f.errorsAsJson) else BadRequest(views.html.feedback.create(f))

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
        feedbackService.create(moreFeedback).map { id =>
          sendMessageEmail(moreFeedback)
          if (isAjax) Ok(id)
          else Redirect(controllers.portal.routes.Portal.index())
            .flashing("success" -> "feedback.thanks.message")
        }
      }
    )
  }

  def list = AdminAction.async { implicit request =>
    feedbackService.list(PageParams.fromRequest(request),
        params = Map("order" -> "-createdAt")).map { flist =>
      Ok(views.html.feedback.list(flist.copy(items = flist.filter(_.text.isDefined))))
    }
  }
}
