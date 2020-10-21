package actors.ingest

import akka.actor.{Actor, ActorRef}
import services.ingest.IngestApi
import services.ingest.IngestApi.IngestJob

import scala.concurrent.ExecutionContext

case class IngestActor(ingestApi: IngestApi)(implicit exec: ExecutionContext) extends Actor {
  override def receive: Receive = waiting

  def waiting: Receive = {
    case job: IngestJob => context.become(run(job))
  }

  def run(job: IngestJob): Receive = {
    case chan: ActorRef => ingestApi.run(job, chan).onComplete { _ =>
      // Terminate the actor...
      context.stop(self)
    }
  }
}
