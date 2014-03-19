package models

import play.api.libs.json.{Format, Json}
import play.api.data.{Forms, Form}

/**
 * Subset of user data that is editable by admin users.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class AdminUserData(active: Boolean, staff: Boolean)

object AdminUserData {
  def fromUserProfile(user: UserProfile): AdminUserData = new AdminUserData(
    active = user.model.active,
    staff = user.account.exists(_.staff)
  )

  implicit val format: Format[AdminUserData] = Json.format[AdminUserData]

  val form = Form(
    Forms.mapping(
      UserProfileF.ACTIVE -> Forms.boolean,
      "staff" -> Forms.boolean
    )(AdminUserData.apply)(AdminUserData.unapply)
  )
}

