package services.ingest

import java.nio.file.Path

import scala.concurrent.Future

trait EadValidator {

  case class Error(line: Int, pos: Int, error: String)

  def validateEad(path: Path): Future[Seq[Error]]
}
