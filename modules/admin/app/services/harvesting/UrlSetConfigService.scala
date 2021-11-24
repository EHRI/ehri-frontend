package services.harvesting

import com.google.inject.ImplementedBy
import models.UrlSetConfig

import scala.concurrent.Future

/**
 * Data access object trait for managing URL set configurations.
 */
@ImplementedBy(classOf[SqlUrlSetConfigService])
trait UrlSetConfigService {
  def get(id: String, ds: String): Future[Option[UrlSetConfig]]
  def save(id: String, ds: String, config: UrlSetConfig): Future[UrlSetConfig]
  def delete(id: String, ds: String): Future[Boolean]
}
