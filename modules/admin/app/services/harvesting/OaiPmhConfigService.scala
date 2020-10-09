package services.harvesting

import com.google.inject.ImplementedBy
import models.OaiPmhConfig

import scala.concurrent.Future

/**
 * Data access object trait for managing OAI-PMH
  * configurations.
 */
@ImplementedBy(classOf[SqlOaiPmhConfigService])
trait OaiPmhConfigService {
  def get(id: String, ds: String): Future[Option[OaiPmhConfig]]
  def save(id: String, ds: String, config: OaiPmhConfig): Future[OaiPmhConfig]
  def delete(id: String, ds: String): Future[Boolean]
}
