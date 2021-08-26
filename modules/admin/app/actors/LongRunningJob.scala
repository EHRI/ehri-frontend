package actors

object LongRunningJob {
  trait Action
  case object Cancel extends Action
}
