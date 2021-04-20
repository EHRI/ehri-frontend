package guice

import com.google.inject.AbstractModule
import config.serviceBaseUrl
import eu.ehri.project.indexing.index.Index
import eu.ehri.project.indexing.index.impl.SolrIndex
import eu.ehri.project.search.solr._
import services.search._

import javax.inject.{Inject, Provider}

private class SolrIndexProvider @Inject()(config: play.api.Configuration) extends Provider[Index] {
  override def get(): Index = new SolrIndex(serviceBaseUrl("solr", config))
}


class SearchModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[Index]).toProvider(classOf[SolrIndexProvider])
    bind(classOf[ResponseParser]).to(classOf[SolrJsonResponseParser])
    bind(classOf[QueryBuilder]).to(classOf[SolrQueryBuilder])
    bind(classOf[SearchIndexMediator]).to(classOf[AkkaStreamsIndexMediator])
    bind(classOf[SearchEngine]).to(classOf[SolrSearchEngine])
    bind(classOf[SearchItemResolver]).to(classOf[GidSearchResolver])
  }
}
