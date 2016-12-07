package controllers.portal

import javax.inject._

import backend.FeedbackService
import backend.rest.cypher.Cypher
import controllers.Components
import controllers.portal.base.PortalController
import play.api.Application
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{RequestHeader, Result}
import utils.PageParams

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Feedback @Inject()(
  components: Components,
  app: Application,
  feedbackService: FeedbackService,
  mailer: MailerClient,
  cypher: Cypher
) extends PortalController {

  override protected implicit val config = components.configuration

  import play.api.data.Form
  import play.api.data.Forms._
  import utils.forms.HoneyPotForm._
  import utils.forms.TimeCheckForm._
  import utils.forms._

  case class CheckFeedbackData(
    timestamp: String,
    blankCheck: String
  ) extends HoneyPotForm with TimeCheckForm

  private def checkFbForm = Form(
    mapping(
      TIMESTAMP -> nonEmptyText,
      BLANK_CHECK -> text.verifying(s => s.isEmpty)
    )(CheckFeedbackData.apply)(CheckFeedbackData.unapply)
      verifying blankFieldIsBlank verifying formSubmissionTime
  )

  private def getCopyMail(feedbackType: Option[models.Feedback.Type.Value]): Seq[String] = {
    import scala.collection.JavaConverters._
    val defaultOpt = config.getStringList("ehri.portal.feedback.copyTo").map(_.asScala)
    ((for {
      ft <- feedbackType
      ct <- config.getStringList(s"ehri.portal.feedback.$ft.copyTo").map(_.asScala)
    } yield ct) orElse defaultOpt).getOrElse(Seq.empty)
  }

  private def sendMessageEmail(feedback: models.Feedback)(implicit request: RequestHeader): Unit = {
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

  def deletePost(id: String) = AdminAction.async { implicit request =>
    feedbackService.delete(id).map(_ => Redirect(controllers.portal.routes.Feedback.list())
      .flashing("success" -> "item.delete.confirmation"))
  }
}
