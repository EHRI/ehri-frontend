package services.data

import models.EntityType

trait EventHandler {
  def handleCreate(items: (EntityType.Value, String)*): Unit
  def handleUpdate(items: (EntityType.Value, String)*): Unit
  def handleDelete(items: (EntityType.Value, String)*): Unit
}
