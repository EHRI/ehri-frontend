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

  private case class TestFacet(value: String) extends FieldFacet {
    val count = 1
    val applied = false
    val name = Some("Language")
  }

  private object TestFacetClass extends FacetClass[TestFacet] {
    override val key: String = "lang"
    override val name: String = "Language"
    override val param: String = "lang"
    override val facets: Seq[TestFacet] = Nil
  }

  private val qs: Map[String,Seq[String]] = ListMap("lang" -> Seq("de", "fr"))


  "Facet query string ops" should {
    "allow adding facet to a query" in {
      val withFacet: String = pathWithFacet(TestFacetClass, TestFacet("en"), "/foobar", qs)
      withFacet must equalTo("/foobar?lang=de&lang=en&lang=fr")
    }

    "allow removing facets from a query" in {
      val withoutFacet: String = pathWithoutFacet(TestFacetClass, TestFacet("fr"), "/foobar", qs)
      withoutFacet must equalTo("/foobar?lang=de")
    }
  }
}
