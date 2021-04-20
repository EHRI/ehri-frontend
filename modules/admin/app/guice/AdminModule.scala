package guice

import com.google.inject.AbstractModule
import services.harvesting._
import services.ingest.{EadValidator, IngestService, RelaxNGEadValidator, WSIngestService}

class AdminModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[IngestService]).to(classOf[WSIngestService])
    bind(classOf[EadValidator]).to(classOf[RelaxNGEadValidator])
    bind(classOf[OaiPmhClient]).to(classOf[WSOaiPmhClient])
    bind(classOf[ResourceSyncClient]).to(classOf[WSResourceSyncClient])
    bind(classOf[HarvestEventService]).to(classOf[SqlHarvestEventService])
  }
}
