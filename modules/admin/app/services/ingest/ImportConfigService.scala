package services.ingest

import com.google.inject.ImplementedBy
import models.ImportConfig

import scala.concurrent.Future

/**
 * Data access object trait for managing OAI-PMH
  * configurations.
 */
@ImplementedBy(classOf[SqlImportConfigService])
trait ImportConfigService {
  def get(id: String, ds: String): Future[Option[ImportConfig]]
  def save(id: String, ds: String, config: ImportConfig): Future[ImportConfig]
  def delete(id: String, ds: String): Future[Boolean]
}
