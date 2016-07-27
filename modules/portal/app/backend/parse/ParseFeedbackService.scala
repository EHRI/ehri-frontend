package backend.parse

import javax.inject.Inject

import models.Feedback
import backend.FeedbackService
import play.api.cache.CacheApi
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

case class ParseFeedbackService @Inject()(
  implicit val cache: CacheApi,
  val config: play.api.Configuration,
  val ws: WSClient,
  val executionContext: ExecutionContext
) extends ParseService[Feedback]("feedback") with FeedbackService
