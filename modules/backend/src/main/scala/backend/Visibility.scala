package backend

import scala.concurrent.Future

trait Visibility {
  def setVisibility[MT: Resource](id: String, data: Seq[String]): Future[MT]
}
