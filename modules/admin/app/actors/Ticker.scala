package actors

import actors.Ticker.Tick
import akka.actor.{Actor, ActorRef, Cancellable}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, FiniteDuration}


// Actor that just prints out a progress indicator
object Ticker {
  case object Run
  case class Tick(tock: String)
  case object Stop
}

case class Ticker(duration: FiniteDuration = 500.millis)(implicit ec: ExecutionContext) extends Actor {
  private val states = Vector("|", "/", "-", "\\")

  override def receive: Receive = init

  def init: Receive = {
    case (actorRef: ActorRef, msg: String) =>
      val cancellable = context.system.scheduler
        .scheduleAtFixedRate(duration, duration * 2, self, Ticker.Run)
      context.become(tick(actorRef, msg, 0, cancellable))
  }

  def tick(actorRef: ActorRef, msg: String, state: Int, cancellable: Cancellable): Receive = {
    case Ticker.Run =>
      actorRef ! Tick(s"$msg... ${states(state)}")
      context.become(tick(actorRef, msg, if (state < 3) state + 1 else 0, cancellable))

    case Ticker.Stop =>
      cancellable.cancel()
  }
}

