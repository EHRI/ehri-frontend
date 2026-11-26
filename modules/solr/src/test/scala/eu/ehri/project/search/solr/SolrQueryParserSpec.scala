package eu.ehri.project.search.solr

import helpers.ResourceUtils
import models.Description
import play.api.test.PlaySpecification
import services.search.FieldFacetClass
import play.api.{Configuration, Environment}
import play.api.libs.json.Json


class SolrQueryParserSpec extends PlaySpecification with ResourceUtils {

  private def jsonResponseString: String = resourceAsString("solrQueryResponse1.json")
  private def config(json: Boolean = false) =
    Configuration.load(Environment.simple(), devSettings = Map("search.jsonFacets" -> json.toString))

  "Solr JSON Query Parser" should {
    val jsonHandler = SolrJsonResponseParser(config())
    "parse the correct number of docs with the right IDs" in {
      val qp = jsonHandler.parse(jsonResponseString)
      val docs = qp.items
      docs.size must equalTo(2)
      docs.find(d => d.itemId == "ehri-cb-638") must beSome
      docs.find(d => d.itemId == "nl") must beSome
    }

    "parse facets correctly" in {
      val allFacets = List(
        FieldFacetClass(
          key=Description.LANG_CODE,
          name= "documentaryUnit." + Description.LANG_CODE,
          param="lang"
        )
      )
      val qp = jsonHandler.parse(jsonResponseString, allFacets, Seq.empty)
      val facetData = qp.facets
      facetData.size must equalTo(1)
      facetData.head.facets.size must equalTo(2)
      facetData.head.facets.head.value must equalTo("eng")
      facetData.head.facets(1).value must equalTo("fre")
      facetData.head.facets.headOption must beSome.which { top =>
        top.value must equalTo("eng")
      }
    }

    "parse JSON facets correctly" in {
      val jsonHandler = SolrJsonResponseParser(config(json = true))
      val allFacets = List(
          FieldFacetClass(
              key=Description.LANG_CODE,
              name= "documentaryUnit." + Description.LANG_CODE,
              param="lang"
              ),
          FieldFacetClass(
              key=Description.CREATION_PROCESS,
              name= "documentaryUnit." + Description.CREATION_PROCESS,
              param="source"
              )
          )
      val qp = jsonHandler.parse(jsonResponseString, allFacets, Seq.empty)
      val facetData = qp.facets
      facetData.size must_== 2
      facetData.head.facets.size must_== 1
      facetData.head.facets.head.value must_== "nld"
      facetData.head.total must_== 1
    }

    "parse highlighting correctly" in {
      val qp = jsonHandler.parse(jsonResponseString)
      val highlightMap: Map[String, Map[String, Seq[String]]] = qp.highlightMap
      highlightMap.size must equalTo(2)
      val itemMap = highlightMap.get("ehri-cb-638-eng-292")
      itemMap must beSome
      val place = itemMap.get.get("place_t")
      place must beSome
      place.get must equalTo(Seq("Active in the Netherlands, <em>Amsterdam</em>."))

      qp.items.head.fields.get("holderName") must beSome.which { v =>
        v must equalTo(Json.arr("EHRI Corporate Bodies"))
      }

      val doc1 = qp.items.head
      doc1.highlights("place_t") must equalTo(Seq("Active in the Netherlands, <em>Amsterdam</em>."))
    }

    "parse collated spellcheck data correctly" in {
      val qp = jsonHandler.parse(jsonResponseString)
      qp.spellcheckSuggestion must beSome.which { case (_, corrected) =>
        corrected must equalTo("purposely mispelled")
      }
    }

    def spellcheckResponse(correctlySpelled: Boolean, collations: Seq[String] = Nil): String = Json.stringify(Json.obj(
      "responseHeader" -> Json.obj("params" -> Json.obj("q" -> "amsterdan")),
      "response" -> Json.obj("numFound" -> 0, "docs" -> Json.arr()),
      "spellcheck" -> Json.obj(
        "suggestions" -> Json.arr("amsterdan", Json.obj("numFound" -> 2, "suggestion" -> Json.arr(
          Json.obj("word" -> "amsterdam", "freq" -> 5),
          Json.obj("word" -> "amstelveen", "freq" -> 500)
        ))),
        "correctlySpelled" -> correctlySpelled,
        "collations" -> collations
      )
    ))

    "use Solr's top suggestion for a single misspelled word" in {
      val qp = jsonHandler.parse(spellcheckResponse(correctlySpelled = false))
      qp.spellcheckSuggestion must beSome(("amsterdan", "amsterdam"))
    }

    "not suggest anything if the query is correctly spelled" in {
      val qp = jsonHandler.parse(spellcheckResponse(correctlySpelled = true, Seq("collation", "amstelveen")))
      qp.spellcheckSuggestion must beNone
    }

    "parse non-collated spellcheck data correctly" in {
      val qp = jsonHandler.parse(resourceAsString("solrQueryResponse2.json"))
      qp.spellcheckSuggestion must beSome.which { case (_, corrected) =>
        corrected must equalTo("purposely")
      }
    }
  }
}
