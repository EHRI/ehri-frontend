package backend.parse

import javax.inject.Inject

import models.Feedback
import backend.FeedbackService
import play.api.cache.CacheApi
import play.api.libs.ws.WSClient

case class ParseFeedbackService @Inject()(implicit cache: CacheApi, config: play.api.Configuration, ws: WSClient) extends ParseService[Feedback]("feedback") with FeedbackService
