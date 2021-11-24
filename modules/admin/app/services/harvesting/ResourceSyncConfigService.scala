package services.harvesting

import com.google.inject.ImplementedBy
import models.ResourceSyncConfig

import scala.concurrent.Future

/**
 * Data access object trait for managing ResourceSync
  * configurations.
 */
@ImplementedBy(classOf[SqlResourceSyncConfigService])
trait ResourceSyncConfigService {
  def get(id: String, ds: String): Future[Option[ResourceSyncConfig]]
  def save(id: String, ds: String, config: ResourceSyncConfig): Future[ResourceSyncConfig]
  def delete(id: String, ds: String): Future[Boolean]
}
