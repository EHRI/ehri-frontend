package models

import play.api.libs.json.Format
import play.api.mvc.PathBindable
import utils.EnumUtils
import utils.binders.bindableEnum
import utils.db.StorableEnum

object FileStage extends Enumeration with StorableEnum {
  val Config = Value("config")
  val Input = Value("input")
  val Output = Value("output")

  implicit val _binder: PathBindable[FileStage.Value] = bindableEnum(FileStage)
  implicit val _format: Format[FileStage.Value] = EnumUtils.enumFormat(FileStage)
}
