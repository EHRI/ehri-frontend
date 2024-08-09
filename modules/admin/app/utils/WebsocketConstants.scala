package utils

object WebsocketConstants {
  /**
    * Message that terminates a long-lived streaming response, such
    * as the search index update job.
    */
  val DONE_MESSAGE = "Done"
  val INFO_MESSAGE = "Info"
  val ERR_MESSAGE = "Error"
}
