package backend

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait EventHandler {
  def handleCreate(id: String): Unit
  def handleUpdate(id: String): Unit
  def handleDelete(id: String): Unit
}
