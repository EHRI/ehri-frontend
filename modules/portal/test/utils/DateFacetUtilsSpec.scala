package utils

import com.google.inject.Inject
import play.api.i18n.{MessagesApi, Messages}
import play.api.test.PlaySpecification
import utils.search.{End, Start, Val}

class DateFacetUtilsSpec extends PlaySpecification with play.api.i18n.I18nSupport {
  import utils.DateFacetUtils._

  implicit val application = new play.api.inject.guice.GuiceApplicationBuilder().build
  implicit val messagesApi = application.injector.instanceOf[MessagesApi]

  val dateFacetUtils = new DateFacetUtils()(messagesApi)

  "date utils" should {
    "format correctly as Solr" in {
      dateFacetUtils.formatAsQuery("1940-1980") must equalTo(Val(startDate(1940)) to Val(endDate(1980)))
      dateFacetUtils.formatAsQuery("1940-") must equalTo(Val(startDate(1940)) to End)
      dateFacetUtils.formatAsQuery("1940-1940") must equalTo(Val(startDate(1940)) to Val(endDate(1940)))
      dateFacetUtils.formatAsQuery("-1980") must equalTo(Start to Val(endDate(1980)))
      // Parse invalid ranges sensibly - ranges with
      // end less than start should be swapped.
      dateFacetUtils.formatAsQuery("1980-1940") must equalTo(Val(startDate(1940)) to Val(endDate(1980)))
    }

    "correctly convert to readable form" in {
      // NB: We need a request in scope for i18n, but since there's
      // no application (and therefore messages files) we don't
      // actually get a language-aware string out
      dateFacetUtils.formatReadable("1940-1980") must equalTo(Some(Messages(DATE_PARAM + ".between", 1940, 1980)))
      dateFacetUtils.formatReadable("1980-1940") must equalTo(Some(Messages(DATE_PARAM + ".between", 1940, 1980)))
      dateFacetUtils.formatReadable("1940-") must equalTo(Some(Messages(DATE_PARAM + ".after", 1940)))
      dateFacetUtils.formatReadable("-1980") must equalTo(Some(Messages(DATE_PARAM + ".before", 1980)))
      dateFacetUtils.formatReadable("1940-1940") must equalTo(Some(Messages(DATE_PARAM + ".exact", 1940, 1980)))
      dateFacetUtils.formatReadable("-") must equalTo(Some(Messages(DATE_PARAM + ".all")))
    }
  }
}
