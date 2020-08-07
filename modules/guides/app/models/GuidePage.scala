package models

import anorm.SqlParser._
import anorm._
import play.api.data.Form
import play.api.data.Forms._
import play.core.parsers.FormUrlEncodedParser
import utils.db.StorableEnum


case class GuidePage(
  id: Option[Long] = None,
  layout: GuidePage.Layout.Value,
  name: String,
  path: String,
  position: GuidePage.MenuPosition.Value,
  content: String,
  parent: Option[Long] = None,
  description: Option[String] = None,
  params: Option[String] = None
) {
  def getParams: Map[String,Seq[String]] = {
    params match {
      case Some(str) => FormUrlEncodedParser.parse(str)
      case _ => Map.empty
    }
  }
}

object GuidePage {
  import utils.EnumUtils.enumMapping

  val PREFIX = "guidePage"
  val OBJECTID = "id"
  val PATH = "path"
  val NAME = "name"
  val LAYOUT = "layout"
  val POSITION = "position"
  val CONTENT = "content"
  val PARENT = "parent"
  val PARAMS = "params"
  val DESCRIPTION = "description"

  object Layout extends Enumeration with StorableEnum {
    val Markdown = Value("md")
    val Html = Value("html")
    val Organisation = Value("organisation")
    val Person = Value("person")
    val Map = Value("map")
    val Timeline = Value("timeline")

    def isFreeText(v: Value): Boolean = Set(Markdown, Html, Timeline) contains v
  }

  object MenuPosition extends Enumeration with StorableEnum {
    val Top = Value("top")
    val Side = Value("side")
    val Nowhere = Value("nowhere")
  }

  implicit val form: Form[models.GuidePage] = Form(
    mapping(
      OBJECTID -> ignored(Option.empty[Long]),
      LAYOUT -> enumMapping(Layout),
      NAME -> nonEmptyText,
      PATH -> nonEmptyText.verifying("guidePage.path.constraint.validity",
        p => p.matches("[0-9a-zA-Z\\-]+")),
      POSITION -> enumMapping(MenuPosition),
      CONTENT -> nonEmptyText,
      PARENT -> optional(longNumber),
      DESCRIPTION -> optional(nonEmptyText),
      PARAMS -> optional(nonEmptyText)
    )(GuidePage.apply)(GuidePage.unapply)
  )

  def blueprint(guideId: Option[Long]): GuidePage = {
    GuidePage(None, Layout.Markdown, "", "", MenuPosition.Side, "", guideId)
  }

  val rowExtractor: RowParser[GuidePage] = {
    get[Option[Long]](OBJECTID) ~
    get[Layout.Value](LAYOUT) ~
    get[String](NAME) ~
    get[String](PATH) ~
    get[MenuPosition.Value](POSITION) ~
    get[String](CONTENT) ~
    get[Option[Long]]("research_guide_id") ~ 
    get[Option[String]](DESCRIPTION) ~ 
    get[Option[String]](PARAMS) map {
      case oid ~ layout ~ name ~ path ~ menu ~ query ~ pid ~ description ~ params  =>
        GuidePage(oid, layout, name, path, menu, query, pid, description, params)
    }
  }

  def faceted: GuidePage = {
    GuidePage(
      None,
      Layout.Markdown,
      "guides.faceted",
      "browse",
      MenuPosition.Top,
      "",
      None,
      None
    )
  }

  def document(title: Option[String] = None): GuidePage = {
    GuidePage(None, Layout.Markdown, title.getOrElse("Documents"), "documents", MenuPosition.Top, "", None, None)
  }

  def repository(title: Option[String] = None): GuidePage = {
    GuidePage(None, Layout.Markdown, title.getOrElse("Repository"), "repository", MenuPosition.Top, "", None, None)
  }
}


