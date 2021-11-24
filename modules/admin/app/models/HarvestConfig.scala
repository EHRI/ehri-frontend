package models

import play.api.libs.json.{Json, Reads, Writes}

trait HarvestConfig {
  def src: ImportDataset.Src.Value
}

object HarvestConfig {
  implicit val _reads: Reads[HarvestConfig] = Reads { json =>
    json.validate[OaiPmhConfig]
      .orElse(json.validate[ResourceSyncConfig])
      .orElse(json.validate[UrlSetConfig])
  }

  implicit val _writes: Writes[HarvestConfig] = Writes {
    case config: OaiPmhConfig => Json.toJson(config)
    case config: ResourceSyncConfig => Json.toJson(config)
    case config: UrlSetConfig => Json.toJson(config)
    // FIXME: error here?
    case config => throw new Exception("Unknown config type")
  }
}
