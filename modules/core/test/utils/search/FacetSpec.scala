package utils.search

import play.api.test.PlaySpecification

import scala.collection.immutable.ListMap

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class FacetSpec extends PlaySpecification {

  private val testData = Map(
    "lang" -> Seq("en", "fr", "de")
  )

  private val testFacetClass = FieldFacetClass(
    key = "lang",
    name = "Language",
    param = "lang"
  )

  private val qs: Map[String,Seq[String]] = ListMap("lang" -> Seq("de", "fr"))


  "Facet query string ops" should {
    "allow adding facet to a query" in {
      val withFacet: String = pathWithFacet(testFacetClass, FieldFacet("en"), "/foobar", qs)
      withFacet must equalTo("/foobar?lang=de&lang=en&lang=fr")
    }

    "allow removing facets from a query" in {
      val withoutFacet: String = pathWithoutFacet(testFacetClass, FieldFacet("fr"), "/foobar", qs)
      withoutFacet must equalTo("/foobar?lang=de")
    }
  }
}
