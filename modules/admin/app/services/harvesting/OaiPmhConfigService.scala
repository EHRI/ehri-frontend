package services.harvesting

import com.google.inject.ImplementedBy
import models.admin.OaiPmhConfig

import scala.concurrent.Future

/**
 * Data access object trait for managing OAI-PMH
  * configurations.
 */
@ImplementedBy(classOf[SqlOaiPmhConfigService])
trait OaiPmhConfigService {
  def get(id: String): Future[Option[OaiPmhConfig]]
  def save(id: String, config: OaiPmhConfig): Future[OaiPmhConfig]
  def delete(id: String): Future[Boolean]
}
