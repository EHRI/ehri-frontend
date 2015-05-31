package backend.parse

import javax.inject.Inject

import models.Feedback
import backend.FeedbackDAO
import play.api.cache.CacheApi

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class ParseFeedbackDAO @Inject()(implicit cache: CacheApi, app: play.api.Application) extends ParseDAO[Feedback]("feedback") with FeedbackDAO
