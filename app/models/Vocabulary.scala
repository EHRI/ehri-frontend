package models

import base._

import models.base.Persistable
import defines.EntityType
import play.api.libs.json._
import defines.EnumUtils.enumWrites
import models.json._
import play.api.i18n.Lang
import play.api.libs.functional.syntax._

object VocabularyType extends Enumeration {
  type Type = Value
}

object VocabularyF {
  val NAME = "name"
  val DESCRIPTION = "description"

  lazy implicit val vocabularyFormat: Format[VocabularyF] = json.VocabularyFormat.restFormat

  implicit object Converter extends RestConvertable[VocabularyF] with ClientConvertable[VocabularyF] {
    lazy val restFormat = models.json.VocabularyFormat.restFormat
    lazy val clientFormat = Json.format[VocabularyF]
  }
}


case class VocabularyF(
  isA: EntityType.Value = EntityType.Vocabulary,
  id: Option[String],
  identifier: String,
  name: Option[String],
  description: Option[String]
) extends Model with Persistable


object Vocabulary {
  final val VOCAB_REL = "inCvoc"
  final val NT_REL = "narrower"
}

object VocabularyMeta {
  implicit object Converter extends ClientConvertable[VocabularyMeta] with RestReadable[VocabularyMeta] {
    val restReads = models.json.VocabularyFormat.metaReads

    val clientFormat: Format[VocabularyMeta] = (
      __.format[VocabularyF](VocabularyF.Converter.clientFormat) and
        nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
        (__ \ "event").formatNullable[SystemEventMeta](SystemEventMeta.Converter.clientFormat)
      )(VocabularyMeta.apply _, unlift(VocabularyMeta.unapply _))


  }
}


case class VocabularyMeta(
  model: VocabularyF,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEventMeta]
) extends AnyModel
  with MetaModel[VocabularyF]
  with Accessible {

  override def toStringLang(implicit lang: Lang): String = model.name.getOrElse(id)
}