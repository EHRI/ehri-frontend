package services.harvesting

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import models.HarvestEvent.HarvestEventType
import models.{HarvestEvent, UserProfile}
import services.harvesting.EventDb.Query

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}


private object EventDb {
  case class Query(repoId: String, datasetId: Option[String] = None, jobId: Option[String] = None)
}

private class EventDb extends Actor {
  var events = List.empty[HarvestEvent]

  override def receive: Receive = {
    case e: HarvestEvent =>
      events :+= e
      sender() ! e

    case Query(repoId, datasetId, jobId) =>
      sender() ! events
        .filter(e => e.repoId == repoId
                  && (datasetId.isEmpty || datasetId.contains(e.datasetId))
                  && (jobId.isEmpty || jobId.contains(e.jobId))
        ).sortBy(_.created)
  }
}

case class MockHarvestEventService()(implicit mat: Materializer, as: ActorSystem, ec: ExecutionContext) extends HarvestEventService {

  private implicit val timeout: Timeout = 1.second
  private val db = as.actorOf(Props(new EventDb))

  override def get(repoId: String, datasetId: Option[String], jobId: Option[String]) =
    db.ask(Query(repoId, datasetId, jobId)).mapTo[List[HarvestEvent]]

  override def save(repoId: String, datasetId: String, jobId: String, info: Option[String])(implicit userOpt: Option[UserProfile]) = synchronized {
    db.ask(HarvestEvent(repoId, datasetId, jobId, userOpt.map(_.id), HarvestEventType.Started, info, Instant.now())).map { _ =>
      new HarvestEventHandle {
        override def close(): Future[Unit] =
          db.ask(HarvestEvent(repoId, datasetId, jobId, userOpt.map(_.id), HarvestEventType.Completed, info, Instant.now())).map(_ => ())

        override def cancel(): Future[Unit] =
          db.ask(HarvestEvent(repoId, datasetId, jobId, userOpt.map(_.id), HarvestEventType.Cancelled, info, Instant.now())).map(_ => ())

        override def error(t: Throwable): Future[Unit] =
          db.ask(HarvestEvent(repoId, datasetId, jobId, userOpt.map(_.id), HarvestEventType.Errored, info, Instant.now())).map(_ => ())
      }
    }
  }
}
