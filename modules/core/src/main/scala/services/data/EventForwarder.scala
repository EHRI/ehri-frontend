package services.data

import com.github.f4b6a3.uuid.alt.GUID
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, Terminated}

import java.util.UUID
import javax.inject.Inject

object EventForwarder {
  case class Subscribe(actorRef: ActorRef)
  case class Unsubscribe(actorRef: ActorRef)

  sealed trait Action {
    def ids: Seq[String]
    def uuid: UUID
  }
  case class Create(ids: Seq[String], uuid: UUID = GUID.v7().toUUID) extends Action
  case class Update(ids: Seq[String], uuid: UUID = GUID.v7().toUUID) extends Action
  case class Delete(ids: Seq[String], uuid: UUID = GUID.v7().toUUID) extends Action
}

class EventForwarder @Inject() extends Actor with ActorLogging {
  import EventForwarder._

  def forward(subs: Set[ActorRef]): Receive = {
    case Subscribe(sub) =>
      log.debug("Subscribed {}", sub)
      context.watch(sub)
      context.become(forward(subs + sub))

    case Unsubscribe(sub) =>
      log.debug("Unsubscribed {}", sub)
      context.unwatch(sub)
      context.become(forward(subs - sub))

    case Terminated(sub) =>
      log.debug("Terminated {}", sub)
      context.unwatch(sub)
      context.become(forward(subs - sub))

    case msg =>
      subs.foreach(_ ! msg)
  }

  def receive: Receive = {
    // If we're here we don't have any subscribers yet, so
    // nothing to do unless we receive an actor...
    case Subscribe(sub) =>
      context.become(forward(Set.empty))
      self ! Subscribe(sub)
  }
}


