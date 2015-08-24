package eu.ehri.project.search.solr

import helpers.ResourceUtils
import play.api.test.PlaySpecification
import utils.search.FieldFacetClass
import models.base.Description


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class SolrQueryParserSpec extends PlaySpecification with ResourceUtils {

  implicit val app = new play.api.inject.guice.GuiceApplicationBuilder().build()
  private def jsonResponseString: String = resourceAsString("solrQueryResponse1.json")

  "Solr JSON Query Parser" should {
    val jsonHandler = JsonResponseHandler(app)
    "parse the correct number of docs with the right IDs" in {
      val qp = jsonHandler.getResponseParser(jsonResponseString)
      val docs = qp.items
      docs.size must equalTo(2)
      docs.find(d => d.itemId == "ehri-cb-638") must beSome
      docs.find(d => d.itemId == "nl") must beSome
    }

    "parse facets correctly" in {
      val qp = jsonHandler.getResponseParser(jsonResponseString)
      val allFacets = List(
        FieldFacetClass(
          key=Description.LANG_CODE,
          name= "documentaryUnit." + Description.LANG_CODE,
          param="lang"
        )
      )
      val facetData = qp.extractFacetData(List.empty, allFacets)
      facetData.size must equalTo(1)
      facetData.head.count must equalTo(2)
      facetData.head.facets.head.value must equalTo("eng")
      facetData.head.facets(1).value must equalTo("fre")
      facetData.head.facets.headOption must beSome.which { top =>
        top.value must equalTo("eng")
      }
    }

    "parse highlighting correctly" in {
      val qp = jsonHandler.getResponseParser(jsonResponseString)
      val highlightMap: Map[String, Map[String, Seq[String]]] = qp.highlightMap
      highlightMap.size must equalTo(2)
      val itemMap = highlightMap.get("ehri-cb-638-eng-292")
      itemMap must beSome
      val place = itemMap.get.get("place_t")
      place must beSome
      place.get must equalTo(Seq("Active in the Netherlands, <em>Amsterdam</em>."))

      qp.items.head.fields.get("holderName") must beSome.which { v =>
        v must equalTo("EHRI Corporate Bodies")
      }

      val doc1 = qp.items.head
      doc1.highlights.get("place_t").get must equalTo(Seq("Active in the Netherlands, <em>Amsterdam</em>."))
    }

    "parse collated spellcheck data correctly" in {
      val qp = jsonHandler.getResponseParser(jsonResponseString)
      qp.spellcheckSuggestion must beSome.which { case (_, corrected) =>
        corrected must equalTo("purposely mispelled")
      }
    }

    "parse non-collated spellcheck data correctly" in {
      val qp = jsonHandler.getResponseParser(resourceAsString("solrQueryResponse2.json"))
      qp.spellcheckSuggestion must beSome.which { case (_, corrected) =>
        corrected must equalTo("purposely")
      }
    }
  }
}
