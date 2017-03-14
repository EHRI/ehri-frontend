package eu.ehri.project.search.solr

import play.api.test.PlaySpecification
import utils.search._

class SolrQueryBuilderSpec extends PlaySpecification {
  val testFieldFacetClass = FieldFacetClass(
      key = "languageCode",
      name = "facet.languageCode",
      param = "lang",
      display = FacetDisplay.Choice,
      sort = FacetSort.Name
  )

  val testQueryFacetClass = QueryFacetClass(
    key="charCount",
    name="Level of detail",
    param="lod",
    render=s => "facet.lod." + s,
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

      val filters: Seq[String] = SolrQueryBuilder.facetFilters(
        List(testFieldFacetClass, testQueryFacetClass), fieldFacets).map(_._2)
      filters.headOption must beSome.which { f =>
        f must equalTo("{!tag=languageCode}languageCode:(\"eng\" \"deu\")")
      }
    }

    "generate query facet filters correctly" in {
      val queryFacets: List[AppliedFacet] = List(
        AppliedFacet("charCount", List("medium", "high"))
      )
      val filters: Seq[String] = SolrQueryBuilder.facetFilters(
        List(testFieldFacetClass, testQueryFacetClass), queryFacets).map(_._2)
      filters.size must equalTo(1)
      filters must contain("charCount:([201 TO 5000] [5001 TO *])")
    }

    "generate facet parameters correctly" in {
      // These are the FacetParams that our test facets should generate -
      // one for the field facet and one for each range in the query facet.
      val fq1 = "facet.field" -> "{!ex=languageCode}languageCode"
      val fq2 = "f.languageCode.facet.sort" -> "index"
      val fq3 = "facet.query" -> "charCount:[0 TO 200]"
      val fq4 = "facet.query" -> "charCount:[201 TO 5000]"
      val fq5 = "facet.query" -> "charCount:[5001 TO *]"

      val facets: Seq[(String, String)] =
        List(testFieldFacetClass, testQueryFacetClass).flatMap(SolrFacetParser.facetAsParams)
      facets.size must equalTo(5)
      facets must contain(fq1)
      facets must contain(fq2)
      facets must contain(fq3)
      facets must contain(fq4)
      facets must contain(fq5)
    }

    "handle string escaping" in {
      SolrQueryBuilder.escape("\"[]\\") must equalTo("\\\"\\[\\]\\\\")
    }
  }
}
