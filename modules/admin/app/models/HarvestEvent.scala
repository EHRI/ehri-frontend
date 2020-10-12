package models

import java.time.Instant

import utils.db.StorableEnum

object HarvestEvent {
  object HarvestEventType extends Enumeration with StorableEnum {
    val Started = Value("started")
    val Cancelled = Value("cancelled")
    val Errored = Value("errored")
    val Completed = Value("completed")
  }
}

case class HarvestEvent(
  repoId: String,
  jobId: String,
  datasetId: String,
  userId: Option[String],
  eventType: HarvestEvent.HarvestEventType.Value,
  info: Option[String],
  created: Instant
)
