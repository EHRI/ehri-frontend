package controllers.institutions

import models.FileStage
import play.api.Configuration
import play.api.mvc.RequestHeader

/**
  * Helpers for controllers which need to access files from storage
  * via Repository and dataset IDs and their associated bucket.
  */
trait StorageHelpers {

  protected def config: Configuration

  protected def instance(implicit request: RequestHeader): String =
    config.getOptional[String]("storage.instance").getOrElse(request.host)

  protected def prefix(id: String, ds: String, stage: FileStage.Value)(implicit request: RequestHeader): String =
    s"$instance/ingest-data/$id/$ds/$stage/"
}
