package services.harvesting

import java.time.Instant

import models.HarvestEvent.HarvestEventType
import models.{HarvestEvent, UserProfile}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future.{successful => immediate}

case class MockHarvestEventService(
  events: ListBuffer[HarvestEvent] = collection.mutable.ListBuffer.empty
) extends HarvestEventService {
  override def get(repoId: String) =
    immediate(events.filter(_.repoId == repoId).sortBy(_.created))

  override def get(repoId: String, jobId: String) =
    immediate(events.filter(e => e.repoId == repoId && e.jobId == jobId).sortBy(_.created))

  override def save(repoId: String, jobId: String, info: Option[String])(implicit userOpt: Option[UserProfile]) = {
    events += HarvestEvent(repoId, jobId, userOpt.map(_.id), HarvestEventType.Started, info, Instant.now())
    immediate(new HarvestEventHandle {
      override def close() = immediate {
        events += HarvestEvent(repoId, jobId, userOpt.map(_.id), HarvestEventType.Completed, info, Instant.now())
        ()
      }

      override def cancel() = immediate {
        events += HarvestEvent(repoId, jobId, userOpt.map(_.id), HarvestEventType.Cancelled, info, Instant.now())
        ()
      }

      override def error(t: Throwable) = immediate {
        events += HarvestEvent(repoId, jobId, userOpt.map(_.id), HarvestEventType.Errored, info, Instant.now())
        ()
      }
    })
  }
}
