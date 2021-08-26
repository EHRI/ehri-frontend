package actors.harvesting

object Harvester {
  sealed trait HarvestAction
  case class Cancelled(done: Int, fresh: Int, secs: Long) extends HarvestAction
  case class Completed(done: Int, fresh: Int, secs: Long) extends HarvestAction
  case class DoneFile(id: String) extends HarvestAction
  case class Error(e: Throwable) extends HarvestAction
  case class ToDo(num: Int) extends HarvestAction
  case object Starting extends HarvestAction

  trait HarvestJob {
    def repoId: String
    def datasetId: String
    def jobId: String
  }
}
