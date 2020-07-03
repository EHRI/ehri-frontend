package defines

import play.api.mvc.PathBindable
import utils.binders.bindableEnum
import utils.db.StorableEnum

object FileStage extends Enumeration with StorableEnum {
  val Ingest = Value("ingest")
  val Upload = Value("upload")
  val OaiPmh = Value("oaipmh")
  val Rs = Value("rs")

  implicit val _binder: PathBindable[FileStage.Value] = bindableEnum(FileStage)

}
