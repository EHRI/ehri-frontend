package models

import java.time.Instant

case class ImportDataset(
  repoId: String,
  id: String,
  name: String,
  src: String,
  created: Instant,
  notes: Option[String] = None
)

case class ImportDatasetInfo(
  id: String,
  name: String,
  src: String,
  notes: Option[String] = None
)
