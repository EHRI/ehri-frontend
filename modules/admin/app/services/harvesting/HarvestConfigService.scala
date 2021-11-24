package services.harvesting

import models.HarvestConfig

import scala.concurrent.Future

/**
 * Data access object trait for managing harvest
  * configurations.
 */
trait HarvestConfigService {
  def get(id: String, ds: String): Future[Option[HarvestConfig]]
  def save(id: String, ds: String, config: HarvestConfig): Future[HarvestConfig]
  def delete(id: String, ds: String): Future[Boolean]
}
