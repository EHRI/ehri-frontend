package services.ingest

import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.PathBindable

import scala.concurrent.Future

case class Coreference(text: String, targetId: String, setId: String)
object Coreference {
  implicit val _writes: Writes[Coreference] = Json.writes[Coreference]
  implicit val _reads: Reads[Coreference] = Json.reads[Coreference]
}

trait CoreferenceService {

  /**
    * Fetch existing coreferences for the given repository.
    *
    * @param id the repository ID
    * @return a sequence of coreferences
    */
  def get(id: String): Future[Seq[Coreference]]

  /**
    * Save a coreference table for the given repository.
    *
    * @param id the repository ID
    */
  def save(id: String, refs: Seq[Coreference]): Future[Unit]

  /**
    * Delete a reference.
    *
    * @param repoId the repository ID
    * @param cid the coreference value ID
    * @return the number of items deleted
    */
  def delete(repoId: String, cid: Int): Future[Int]
}
