import javax.inject.Inject
import play.api.http.HttpFilters
import play.filters.csrf.CSRFFilter

class Filters @Inject() (
  csrfFilter: CSRFFilter
) extends HttpFilters {

  val filters = Seq(csrfFilter)
}
