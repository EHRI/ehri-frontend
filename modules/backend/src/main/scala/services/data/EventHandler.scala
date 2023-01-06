package services.data

import akka.actor.ActorRef

trait EventHandler {
  def subscribe(actorRef: ActorRef): Unit = ()
  def unsubscribe(actorRef: ActorRef): Unit = ()
  def handleCreate(ids: String*): Unit
  def handleUpdate(ids: String*): Unit
  def handleDelete(ids: String*): Unit
}
