package forms

import play.api.{ConfigLoader, Configuration}

/**
  * This class provides helpers for reading configuration affecting how
  * form's for an entity type are displayed, including hiding values,
  * setting input sizes, marking fields required and setting default
  * values.
  */
case class FormConfig(private val config: Option[Configuration], update: Boolean) {
  def hidden(field: String): Boolean =
    config.flatMap(_.getOptional[Boolean](s"$field.hidden")).getOrElse(false)

  def default[T: ConfigLoader](field: String): Option[T] =
    if (update) None else config.flatMap(_.getOptional[T](s"$field.default"))

  def hint(field: String): Option[String] =
    config.flatMap(_.getOptional[String](s"$field.hint"))

  def rows(field: String): Option[Int] =
    config.flatMap(_.getOptional[Int](s"$field.rows"))

  def required(field: String): Option[Boolean] =
    config.flatMap(_.getOptional[Boolean](s"$field.required"))
}

case class FormConfigBuilder(et: defines.EntityType.Value, config: Configuration) {
  private val path = s"formConfig.$et"
  def forUpdate: FormConfig = FormConfig(config.getOptional[Configuration](path), update = true)
  def forCreate: FormConfig = FormConfig(config.getOptional[Configuration](path), update = false)
}
