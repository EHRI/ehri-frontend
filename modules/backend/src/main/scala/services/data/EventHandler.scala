package services.data

trait EventHandler {
  def handleCreate(ids: String*): Unit
  def handleUpdate(ids: String*): Unit
  def handleDelete(ids: String*): Unit
}
