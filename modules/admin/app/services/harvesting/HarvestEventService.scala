package services.harvesting

import com.google.inject.ImplementedBy
import models.{HarvestEvent, UserProfile}
import models.HarvestEvent.HarvestEventType

import scala.concurrent.Future

@ImplementedBy(classOf[SqlHarvestEventService])
trait HarvestEventService {
  def get(repoId: String): Future[Seq[HarvestEvent]]

  def get(repoId: String, jobId: String): Future[Seq[HarvestEvent]]

  def save(repoId: String, jobId: String, eventType: HarvestEventType.Value, info: Option[String] = None)(implicit userOpt: Option[UserProfile]): Future[Unit]
}
