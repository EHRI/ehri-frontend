package models

import play.api.libs.json.{Reads, Format, Json}
import play.api.data.Form
import play.api.data.Forms._
import play.api.Mode.Mode
import org.joda.time.DateTime

/**
 * @author Thibault Clérice (http://github.com/ponteineptique)
 */
case class GuidesData(
  objectId: Option[Int],
  name: String,
  path: String,
  picture: Option[String],
  description: Option[String]
)

object GuidesData {
  val PREFIX = "guides"
  val OBJECTID = "objectId"
  val PATH = "path"
  val NAME = "name"
  val DESCRIPTION = "description"
  val PICTURE = "picture"

  implicit val form = Form(
    mapping(
      "objectId" -> optional(number),
      "name" -> text,
      "path" -> text,
      "picture" -> optional(nonEmptyText),
      "description" -> optional(text)
    )(GuidesData.apply _)(GuidesData.unapply _)
  )
}


