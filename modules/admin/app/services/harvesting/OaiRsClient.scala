package services.harvesting

import com.google.inject.ImplementedBy
import models.{OaiRsConfig, ResourceLink}

import scala.concurrent.Future


@ImplementedBy(classOf[WSOaiRsClient])
trait OaiRsClient {
  def list(config: OaiRsConfig): Future[Seq[ResourceLink]]

  def audit(config: OaiRsConfig): Future[Boolean]
}
