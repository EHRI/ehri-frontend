package eu.ehri.project.search.solr

import play.api.test.PlaySpecification
import play.api.i18n.Messages
import utils.search._
import com.github.seratch.scalikesolr.request.query.facet.{Value, Param, FacetParam}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class SolrQueryBuilderSpec extends PlaySpecification {
  val testFieldFacetClass = FieldFacetClass(
      key = "languageCode",
      name = Messages("facet.languageCode"),
      param = "lang",
      render = (s: String) => utils.i18n.languageCodeToName(s),
      display = FacetDisplay.Choice,
      sort = FacetSort.Name
  )

  val testQueryFacetClass = QueryFacetClass(
    key="charCount",
    name=Messages("facet.lod"),
    param="lod",
    render=s => Messages("facet.lod." + s),
    facets=List(
      QueryFacet(value = "low", range = Val("0") to Val("200")),
      QueryFacet(value = "medium", range = Val("201") to Val("5000")),
      QueryFacet(value = "high", range = Val("5001") to End)
    ),
    sort = FacetSort.Fixed,
    display = FacetDisplay.List
  )

  "Solr Query Builder" should {
    "generate field facet filters correctly" in {
      val fieldFacets: List[AppliedFacet] = List(
        AppliedFacet("languageCode", List("eng", "deu"))
      )

      val filters: Seq[String] = SolrQueryBuilder.getRequestFilters(
        List(testFieldFacetClass, testQueryFacetClass), fieldFacets)
      filters.headOption must beSome.which { f =>
        f must equalTo("{!tag=languageCode}languageCode:(\"eng\" \"deu\")")
      }
    }

    "generate query facet filters correctly" in {
      val queryFacets: List[AppliedFacet] = List(
        AppliedFacet("charCount", List("medium", "high"))
      )
      val filters: Seq[String] = SolrQueryBuilder.getRequestFilters(
        List(testFieldFacetClass, testQueryFacetClass), queryFacets)
      filters.size must equalTo(1)
      filters must contain("charCount:([201 TO 5000] [5001 TO *])")
    }

    "generate facet parameters correctly" in {
      // These are the FacetParams that our test facets should generate -
      // one for the field facet and one for each range in the query facet.
      val fq1 = new FacetParam(new Param("facet.field"), new Value("{!ex=languageCode}languageCode"))
      val fq2 = new FacetParam(new Param("facet.query"), new Value("charCount:[0 TO 200]"))
      val fq3 = new FacetParam(new Param("facet.query"), new Value("charCount:[201 TO 5000]"))
      val fq4 = new FacetParam(new Param("facet.query"), new Value("charCount:[5001 TO *]"))

      val facets: Seq[FacetParam] = SolrQueryBuilder.getRequestFacets(
        List(testFieldFacetClass, testQueryFacetClass))
      facets.size must equalTo(4)
      facets must contain(fq1)
      facets must contain(fq2)
      facets must contain(fq3)
      facets must contain(fq4)
    }
  }
}
