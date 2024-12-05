package helpers

import eu.ehri.project.search.solr.SolrSearchEngine
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.test.PlaySpecification
import services.search.{PekkoStreamsIndexMediator, SearchEngine, SearchIndexMediator}

/**
 * Override the standard Test runner with non-mock search components.
 */
trait SearchTestRunner extends PlaySpecification with UserFixtures with TestConfiguration {

  override def testSearchComponents: Seq[GuiceableModule] = Seq(
    bind[SearchIndexMediator].to[PekkoStreamsIndexMediator],
    bind[SearchEngine].to[SolrSearchEngine],
  )
}
