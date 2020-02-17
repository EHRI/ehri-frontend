package services.ingest

import java.nio.file.Path

import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future

trait EadValidator {

  case class Error(line: Int, pos: Int, error: String)

  def validateEad(path: Path): Future[Seq[Error]]

  def validateEad(url: Uri): Future[Seq[Error]]

  def validateEad(src: Source[ByteString, _]): Future[Seq[Error]]
}
