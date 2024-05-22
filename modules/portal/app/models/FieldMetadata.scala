package models

import play.api.libs.json.{Format, Reads}
import utils.EnumUtils.enumFormat

import java.time.Instant
import utils.db.StorableEnum

/**
  * This class contains information about fields in the database. The entity type
  * and id must correspond with a model's isA field and the database name of the
  * field respectively, e.g. EntityType.RepositoryDescription and "history".
  *
  * The purpose of this class is to provide
  *   a) documentation to end users;
  *   b) hints for the UI about how to display the field in the admin pages;
  *   c) information for scanning for missing fields in the database.
  *
  * @param entityType the entity type of the field
  *                   (e.g. EntityType.RepositoryDescription)
  * @param id the key/name of the field in the database (e.g. "history")
  * @param name the human-readable name of the field
  * @param description a description of the field
  * @param usage whether the field is mandatory or desirable
  * @param category an optional string denoting the category of the field in
  *                 a large form, e.g. "Identity", "Access", "Description"
  * @param seeAlso a list of URLs for further information about the field or
  *                 related documentation
  */
case class FieldMetadata(
  entityType: EntityType.Value,
  id: String,
  name: String,
  description: Option[String] = None,
  usage: Option[FieldMetadata.Usage.Value] = None,
  category: Option[String] = None,
  default: Option[String] = None,
  seeAlso: Seq[String] = Nil,
  created: Option[Instant] = None,
  updated: Option[Instant] = None
)

object FieldMetadata {
  object Usage extends Enumeration with StorableEnum {
    type Type = Value
    val Mandatory = Value("mandatory")
    val Desirable = Value("desirable")

    implicit val _format: Format[Value] = enumFormat(Usage)
  }

  implicit val _format: Format[FieldMetadata] = play.api.libs.json.Json.format[FieldMetadata]
}

object FieldMetadataInfo {
  implicit val _reads: Reads[FieldMetadataInfo] = play.api.libs.json.Json.reads[FieldMetadataInfo]
}

case class FieldMetadataInfo(
  name: String,
  description: Option[String] = None,
  usage: Option[FieldMetadata.Usage.Value] = None,
  category: Option[String] = None,
  defaultVal: Option[String] = None,
  seeAlso: Seq[String] = Nil
)


