package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{RepositoryF, Entity}
import defines.EntityType

/**
 * Repository model form.
 */
object RepositoryForm {

  import RepositoryF._

  /**
   * Validate an URL substitution pattern. Currently there's
   * only one valid pattern, into which the substition must
   * be {identifier}, i.e. http://collections.ushmm.org/search/irn{identifier}.
   */
  private def validateUrlPattern(s: String) = {
    val replace = "identifier"
    s.contains(s"{$replace}") && utils.forms
        .isValidUrl(s.replaceAll("\\{" + replace + "\\}", "test"))
  }

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.Repository),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=2), // TODO: Increase to > 2, not done yet 'cos of test fixtures
      PUBLICATION_STATUS -> optional(models.forms.enum(defines.PublicationStatus)),
      "descriptions" -> list(IsdiahForm.form.mapping),
      PRIORITY -> optional(number(min = -1, max = 5)),
      URL_PATTERN -> optional(nonEmptyText verifying("errors.badUrlPattern", fields => fields match {
        case pattern => validateUrlPattern(pattern)
      })),
      LOGO_URL -> optional(nonEmptyText verifying("error.badUrl", fields => fields match {
        case url => utils.forms.isValidUrl(url)
      }))
    )(RepositoryF.apply)(RepositoryF.unapply)
  )
}
