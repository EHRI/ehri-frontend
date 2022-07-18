package services.ingest

import play.api.libs.json.{Format, Json}

import scala.concurrent.Future

case class Coreference(text: String, targetId: String, setId: String)
object Coreference {
  implicit val _format: Format[Coreference] = Json.format[Coreference]
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
    * @return the number of items inserted
    */
  def save(id: String, refs: Seq[Coreference]): Future[Int]

  /**
    * Delete a reference.
    *
    * @param repoId the repository ID
    * @param refs the coreference values
    * @return the number of items deleted
    */
  def delete(repoId: String, refs: Seq[Coreference]): Future[Int]
}
