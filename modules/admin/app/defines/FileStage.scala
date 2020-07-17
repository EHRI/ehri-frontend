package defines

import play.api.libs.json.Format
import play.api.mvc.PathBindable
import utils.EnumUtils
import utils.binders.bindableEnum
import utils.db.StorableEnum

object FileStage extends Enumeration with StorableEnum {
  val Upload = Value("upload")
  val OaiPmh = Value("oaipmh")
  val Ingest = Value("ingest")
  val Rs = Value("rs")

  implicit val _binder: PathBindable[FileStage.Value] = bindableEnum(FileStage)
  implicit val _format: Format[FileStage.Value]= EnumUtils.enumFormat(FileStage)
}
