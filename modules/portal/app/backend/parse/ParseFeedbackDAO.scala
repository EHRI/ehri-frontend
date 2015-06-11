package backend.parse

import javax.inject.Inject

import models.Feedback
import backend.FeedbackDAO
import play.api.cache.CacheApi
import play.api.libs.ws.WSClient

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class ParseFeedbackDAO @Inject()(implicit cache: CacheApi, config: play.api.Configuration, ws: WSClient) extends ParseDAO[Feedback]("feedback") with FeedbackDAO
