package models.forms

import play.api.data._
import play.api.data.Forms._

import models.Entity
import models.base.Persistable
import org.joda.time.DateTime
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites

object AnnotationType extends Enumeration {
  type Type = Value
  val Comment = Value("comment")
  val Link = Value("link")
  val Aggregation = Value("aggregation")

}

object AnnotationF {
  val BODY = "body"
  val FIELD = "field"
  val ANNOTATION_TYPE = "annotationType"
  val COMMENT = "comment"
}

case class AnnotationF(
  val id: Option[String],
  val annotationType: AnnotationType.Type,
  val body: Option[String],
  val field: Option[String] = None,
  val comment: Option[String] = None
) extends Persistable {
  val isA = EntityType.Annotation

  def toJson = {
    implicit val dateWrites = play.api.libs.json.Writes.jodaDateWrites("yyyy-MM-dd")
    import AnnotationF._

    Json.obj(
      Entity.ID -> id,
      Entity.TYPE -> isA,
      Entity.DATA -> Json.obj(
        BODY -> body,
        ANNOTATION_TYPE -> annotationType,
        FIELD -> field,
        COMMENT -> comment
      )
    )
  }
}


object AnnotationForm {

  import AnnotationF._

  val form = Form(mapping(
    Entity.ID -> optional(nonEmptyText),
    ANNOTATION_TYPE -> enum(AnnotationType),
    BODY -> optional(nonEmptyText),
    FIELD -> optional(nonEmptyText),
    COMMENT -> optional(nonEmptyText)
  )(AnnotationF.apply)(AnnotationF.unapply))
}
