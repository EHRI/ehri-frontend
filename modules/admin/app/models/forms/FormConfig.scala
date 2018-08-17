package models.forms

import play.api.{ConfigLoader, Configuration}

case class FormConfig(private val config: Option[Configuration], update: Boolean) {
  def hidden(field: String): Boolean =
    config.flatMap(_.getOptional[Boolean](s"$field.hidden")).getOrElse(false)

  def default[T: ConfigLoader](field: String): Option[T] =
    if (update) None else config.flatMap(_.getOptional[T](s"$field.default"))
}

case class FormConfigBuilder(et: defines.EntityType.Value, config: Configuration) {
  private val path = s"formConfig.$et"
  def forUpdate: FormConfig = FormConfig(config.getOptional[Configuration](path), update = true)
  def forCreate: FormConfig = FormConfig(config.getOptional[Configuration](path), update = false)
}
