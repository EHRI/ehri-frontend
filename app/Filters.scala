import javax.inject.Inject

import play.api.http.DefaultHttpFilters
import play.filters.cors.CORSFilter
import play.filters.csrf.CSRFFilter

class Filters @Inject()(
  csrfFilter: CSRFFilter,
  corsFilter: CORSFilter
) extends DefaultHttpFilters(csrfFilter, corsFilter)
