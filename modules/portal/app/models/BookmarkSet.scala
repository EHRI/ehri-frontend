package models

/**
 * Class representing a user bookmark set, e.g. a
 * container for a bunch of bookmarks. On the server
 * this is represented as a virtual unit.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class BookmarkSet(name: String, description: Option[String])
object BookmarkSet {
  val NAME = "name"
  val DESCRIPTION = "description"

  import play.api.data.Form
  import play.api.data.Forms._
  val bookmarkForm: Form[BookmarkSet] = Form(
    mapping(
      NAME -> nonEmptyText,
      DESCRIPTION -> optional(text)
    )(BookmarkSet.apply)(BookmarkSet.unapply)
  )
}

