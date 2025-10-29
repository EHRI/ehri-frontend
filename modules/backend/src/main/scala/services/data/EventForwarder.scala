package services.data

import models.EntityType
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, Terminated}

import javax.inject.Inject

object EventForwarder {
  case class Subscribe(actorRef: ActorRef)
  case class Unsubscribe(actorRef: ActorRef)

  sealed trait Action
  case class Create(items: Seq[(EntityType.Value, String)]) extends Action
  case class Update(items: Seq[(EntityType.Value, String)]) extends Action
  case class Delete(items: Seq[(EntityType.Value, String)]) extends Action
}

class EventForwarder @Inject() () extends Actor with ActorLogging {
  import EventForwarder._

  def forward(subs: Set[ActorRef]): Receive = {
    case Subscribe(sub) =>
      context.watch(sub)
      context.become(forward(subs + sub))

    case Unsubscribe(sub) =>
      context.unwatch(sub)
      context.become(forward(subs - sub))

    case Terminated(sub) =>
      context.unwatch(sub)
      context.become(forward(subs - sub))

    case msg => subs.foreach(_ ! msg)
  }

  def receive: Receive = {
    // If we're here we don't have any subscribers yet, so
    // nothing to do unless we receive an actor...
    case Subscribe(sub) =>
      context.become(forward(Set(sub)))
  }
}


