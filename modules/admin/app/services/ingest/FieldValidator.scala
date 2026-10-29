package services.ingest

import models.AccessPointF.AccessPointType
import models.Description.{LANG_CODE, SOURCE_FILE_ID}
import models.Entity.IDENTIFIER
import models.IsadG

/**
  * The field names recognised by the target import format (ISAD(G)-based,
  * shared by tabular and JSON data), derived from [[IsadG.FIELDS]] - the
  * same field set the real description model is built from - plus a few
  * extra fields the backend also accepts that aren't part of that set.
  */
object FieldSchema {

  // The import format expects "objectIdentifier", not the internal model's
  // "identifier", for legacy reasons.
  private val renames: Map[String, String] = Map(IDENTIFIER -> "objectIdentifier")
  private def rename(field: String): String = renames.getOrElse(field, field)

  // sourceFileId matches individual descriptions for update. One access point
  // field per AccessPointType (personAccessPoint, familyAccessPoint, ...).
  private val extraFields: Set[String] = AccessPointType.values.map(_.toString + "AccessPoint") + SOURCE_FILE_ID

  val allFields: Set[String] = IsadG.FIELDS.flatMap(_._2).map(rename).toSet ++ extraFields
  val requiredFields: Set[String] = Set(rename(IDENTIFIER), LANG_CODE, IsadG.TITLE)
}

trait FieldValidator {
  def validateFields(fields: Seq[String]): Seq[FieldValidationError]
}

/**
  * Checks a flat set of field names (CSV header row, or JSON object keys)
  * against the ISAD(G)-based target schema: required fields must be
  * present, and every given field must be a recognised one.
  */
case class IsadGFieldValidator() extends FieldValidator {
  override def validateFields(fields: Seq[String]): Seq[FieldValidationError] = {
    val provided = fields.toSet
    val missing = (FieldSchema.requiredFields -- provided).toSeq.sorted.map(f =>
      FieldValidationError(f, "missing", s"Required field '$f' is missing"))
    val unexpected = (provided -- FieldSchema.allFields).toSeq.sorted.map(f =>
      FieldValidationError(f, "unexpected", s"Unexpected field '$f'"))
    missing ++ unexpected
  }
}
