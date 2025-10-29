package services.data

import models.EntityType
import org.apache.pekko.actor.ActorRef

trait EventHandler {
  def subscribe(actorRef: ActorRef): Unit = ()
  def unsubscribe(actorRef: ActorRef): Unit = ()
  def handleCreate(items: (EntityType.Value, String)*): Unit
  def handleUpdate(items: (EntityType.Value, String)*): Unit
  def handleDelete(items: (EntityType.Value, String)*): Unit
}
