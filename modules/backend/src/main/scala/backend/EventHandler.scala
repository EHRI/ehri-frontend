package backend

trait EventHandler {
  def handleCreate(id: String): Unit
  def handleUpdate(id: String): Unit
  def handleDelete(id: String): Unit
}
