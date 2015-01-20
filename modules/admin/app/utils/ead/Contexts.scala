package utils.ead

import play.api.Play.current
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object Contexts {
  implicit val exportContext: ExecutionContext = Akka.system.dispatchers.lookup("contexts.ead-export")
}