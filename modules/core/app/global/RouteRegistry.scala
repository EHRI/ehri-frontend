package global

import play.api.mvc.Call

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object RouteRegistry {

  import defines.EntityType

  val urls = collection.mutable.Map.empty[String,String => Call]

  private var default: String => Call = s => new Call("GET", "/")

  def setDefault(c: String => Call): Unit = {
    default = c
  }

  def getDefault: String => Call = default

  def setUrl(e: EntityType.Value, c: String => Call): Unit = {
    urls.put(e, c)
  }
}
