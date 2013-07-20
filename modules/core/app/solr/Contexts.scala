package solr

import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext
import play.api.Play.current

object Contexts {
  implicit val searchIndexExecutionContext: ExecutionContext
      = Akka.system.dispatchers.lookup("index-dispatcher")
}
