package utils

import play.api.data.{Form, Forms}
import defines.EnumUtils.enumMapping
import play.api.libs.json.Format

/**
 * Enum defining modes of visibility for contributed
 * content.
 */
object ContributionVisibility extends Enumeration {

  val PARAM = "visibility"

  /**
   * Only the author.
   */
  val Me = Value("me")

  /**
   * The author and those who belong to groups
   * the author belongs to.
   */
  val Groups = Value("groups")

  /**
   * A specific mix of users and/or groups.
   */
  val Custom = Value("custom")

  /**
   * Binding/unbinding from a form.
   */
  val form = Form(Forms.single(PARAM -> Forms.default(enumMapping(this), ContributionVisibility.Me)))

  /**
   * Binding/unbinding from JSON.
   */
  val format: Format[ContributionVisibility.Value] = defines.EnumUtils.enumFormat(this)
}
