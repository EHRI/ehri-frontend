package models

import java.time.Instant

import utils.db.StorableEnum

object HarvestEvent {
  object HarvestEventType extends Enumeration with StorableEnum {
    val Start = Value("start")
    val Cancelled = Value("cancelled")
    val Complete = Value("complete")
  }
}

case class HarvestEvent(
  repoId: String,
  jobId: String,
  userId: Option[String],
  eventType: HarvestEvent.HarvestEventType.Value,
  info: Option[String],
  created: Instant
)
