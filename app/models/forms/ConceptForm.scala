package models.forms

import play.api.data._
import play.api.data.Forms._

import models.{Annotations, Entity}
import models.base.Persistable
import models.base.DescribedEntity
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites

object ConceptType extends Enumeration {
  type Type = Value

}

object ConceptF {
  val LANGUAGE = "languageCode"
  val PREFLABEL = "prefLabel"
  val ALTLABEL = "altLabel"
  val DEFINITION = "definition"
  val SCOPENOTE = "scopeNote"

}

case class ConceptF(
  val id: Option[String],
  val identifer: String,
  @Annotations.Relation(DescribedEntity.DESCRIBES_REL) val descriptions: List[ConceptDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.Concept

  def toJson = {
    import ConceptF._

    Json.obj(
      Entity.ID -> id,
      Entity.TYPE -> isA,
      Entity.DATA -> Json.obj(
        Entity.IDENTIFIER -> identifer
      ),
      Entity.RELATIONSHIPS -> Json.obj(
        DescribedEntity.DESCRIBES_REL -> Json.toJson(descriptions.map(_.toJson).toSeq)
      )
    )
  }
}

case class ConceptDescriptionF(
  val id: Option[String],
  val languageCode: String,
  val prefLabel: String,
  val altLabels: Option[List[String]] = None,
  val definition: Option[String] = None,
  val scopeNote: Option[String] = None
  ) extends Persistable {
    val isA = EntityType.ConceptDescription

    def toJson = {
      import ConceptF._

      Json.obj(
        Entity.ID -> id,
        Entity.TYPE -> isA,
        Entity.DATA -> Json.obj(
          LANGUAGE -> languageCode,
          PREFLABEL -> prefLabel,
          ALTLABEL -> altLabels,
          DEFINITION -> definition,
          SCOPENOTE -> scopeNote
        )
      )
    }
}


object ConceptDescriptionForm {

  import ConceptF._

  val form = Form(mapping(
    Entity.ID -> optional(nonEmptyText),
    LANGUAGE -> nonEmptyText,
    PREFLABEL -> nonEmptyText,
    ALTLABEL -> optional(list(nonEmptyText)),
    DEFINITION -> optional(nonEmptyText),
    SCOPENOTE -> optional(nonEmptyText)
  )(ConceptDescriptionF.apply)(ConceptDescriptionF.unapply))
}


object ConceptForm {
  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      "descriptions" -> list(ConceptDescriptionForm.form.mapping)
    )(ConceptF.apply)(ConceptF.unapply)
  )
}
