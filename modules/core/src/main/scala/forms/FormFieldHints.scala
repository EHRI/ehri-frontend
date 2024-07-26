package forms

import models.{EntityType, FieldMetadata, FieldMetadataSet}
import play.api.Configuration
import services.datamodel.EntityTypeMetadataService

import scala.concurrent.{ExecutionContext, Future}

/**
  * This trait provides a set of hints for form fields, including
  * whether they should be hidden, have a default value, hint, description,
  * number of rows, and whether they are required.
  */
trait FormFieldHints {
  def name(field: String): Option[String] = None

  def hidden(field: String): Boolean

  def default(field: String): Option[String]

  def hint(field: String): Option[String]

  def description(field: String): Option[String]

  def rows(field: String): Option[Int]

  def required(field: String): Option[Boolean]

  def usage(field: String): Option[FieldMetadata.Usage.Value] = None
}

/**
  * This class provides helpers for reading configuration affecting how
  * form's for an entity type are displayed, including hiding values,
  * setting input sizes, marking fields required and setting default
  * values.
  */
case class ConfigFormFieldHints(private val config: Option[Configuration], update: Boolean) extends FormFieldHints {
  override def hidden(field: String): Boolean =
    config.flatMap(_.getOptional[Boolean](s"$field.hidden")).getOrElse(false)

  override def default(field: String): Option[String] =
    if (update) None else config.flatMap(_.getOptional[String](s"$field.default"))

  override def hint(field: String): Option[String] =
    config.flatMap(_.getOptional[String](s"$field.hint"))

  override def description(field: String): Option[String] =
    config.flatMap(_.getOptional[String](s"$field.description"))

  override def rows(field: String): Option[Int] =
    config.flatMap(_.getOptional[Int](s"$field.rows"))

  override def required(field: String): Option[Boolean] =
    config.flatMap(_.getOptional[Boolean](s"$field.required"))
}

case class ConfigFormFieldHintsBuilder(et: EntityType.Value, config: Configuration) {
  private val path = s"formConfig.$et"
  def forUpdate: Future[FormFieldHints] = Future.successful(ConfigFormFieldHints(config.getOptional[Configuration](path), update = true))
  def forCreate: Future[FormFieldHints] = Future.successful(ConfigFormFieldHints(config.getOptional[Configuration](path), update = false))
}

/**
  * Field hints that are based on the field metadata, with configuration as a fallback.
  */
case class FieldMetaFormFieldHints(base: FormFieldHints, meta: FieldMetadataSet, update: Boolean) extends FormFieldHints {

  override def name(field: String): Option[String] = meta
    .get(field)
    .map(_.name)
    .orElse(base.name(field))

  override def hidden(field: String): Boolean = base.hidden(field)

  override def default(field: String): Option[String] = if (update) None else meta
    .get(field)
    .flatMap(_.defaultVal)
    .orElse(base.default(field))

  override def hint(field: String): Option[String] = base.hint(field)

  override def rows(field: String): Option[Int] = base.rows(field)

  override def description(field: String): Option[String] = meta
    .get(field)
    .flatMap(_.description)
    .orElse(base.description(field))

  override def required(field: String): Option[Boolean] = meta
    .get(field)
    .map(_.usage.contains(FieldMetadata.Usage.Mandatory))
    .orElse(base.required(field))

  override def usage(field: String): Option[FieldMetadata.Usage.Value] = meta
    .get(field)
    .flatMap(_.usage)
    .orElse(base.usage(field))
}

case class FieldMetaFormFieldHintsBuilder(et: EntityType.Value, ets: EntityTypeMetadataService, config: Configuration)(implicit ec: ExecutionContext) {
  // Legacy config fallback...
  private val baseBuilder = ConfigFormFieldHintsBuilder(et, config)

  def forUpdate: Future[FormFieldHints] = for {
    meta <- ets.listEntityTypeFields(et)
    base <- baseBuilder.forUpdate} yield FieldMetaFormFieldHints(base, meta, update = true)

  def forCreate: Future[FormFieldHints] = for {
    meta <- ets.listEntityTypeFields(et)
    base <- baseBuilder.forCreate
  } yield FieldMetaFormFieldHints(base, meta, update = false)
}
