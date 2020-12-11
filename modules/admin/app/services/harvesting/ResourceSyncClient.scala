package services.harvesting

import com.google.inject.ImplementedBy
import models.{ResourceSyncConfig, FileLink}

import scala.concurrent.Future


@ImplementedBy(classOf[WSResourceSyncClient])
trait ResourceSyncClient {
  def list(config: ResourceSyncConfig): Future[Seq[FileLink]]
}
