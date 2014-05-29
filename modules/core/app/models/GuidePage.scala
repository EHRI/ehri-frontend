package models

import play.api.data.Form
import play.api.data.Forms._

import anorm._
import anorm.SqlParser._
import play.api.Play.current
import play.api.db.DB
import language.postfixOps


/**
 * @author Thibault Clérice (http://github.com/ponteineptique)
 */
case class GuidePage(
  objectId: Option[Int],
  layout: String,
  name: String,
  path: String,
  position: String,
  content: String,
  parent: Int
) {
  /*
  * Edit a page
  */
  def update(): Unit = DB.withConnection {
    implicit connection =>
      SQL(
        """
      UPDATE
        research_guide_page
      SET
        layout = {l},
        name = {n},
        path = {p},
        menu = {m},
        cypher = {c},
        research_guide_id = {parent}
      WHERE id = {id}
      LIMIT 1
        """
      ).on('l -> layout, 'n -> name, 'p -> path, 'm -> position, 'c -> content, 'parent -> parent, 'id -> objectId).executeUpdate()
  }

  /*
  * Delete a page
  */
  def delete(): Unit = DB.withConnection {
    implicit connection =>
      SQL("""DELETE FROM research_guide_page WHERE id = {i} LIMIT 1""")
        .on('i -> objectId).executeUpdate()
  }
}

object GuidePage {

  val PREFIX = "guidePage"
  val OBJECTID = "objectId"
  val PATH = "path"
  val NAME = "name"
  val LAYOUT = "layout"
  val POSITION = "position"
  val CONTENT = "content"
  val PARENT = "parent"

  val layouts: List[String] = List(
    "md",
    "organisation",
    "person",
    "map",
    "keyword"
  )

  val positions: List[String] = List(
    "top",
    "side"
  )

  implicit val form = Form(
    mapping(
      "objectId" -> optional(number),
      "layout" -> nonEmptyText,
      "name" -> nonEmptyText,
      "path" -> nonEmptyText,
      "position" -> nonEmptyText,
      "content" -> nonEmptyText,
      "parent" -> number
    )(GuidePage.apply _)(GuidePage.unapply _)
  )

  def blueprint(guideId: Option[Int]): GuidePage = {
    GuidePage(None, "", "", "", "", "", guideId.getOrElse(0))
  }

  val rowExtractor = {
    get[Option[Int]]("id") ~
    get[String]("layout") ~
    get[String]("name") ~
    get[String]("path") ~
    get[String]("menu") ~
    get[String]("cypher") ~
    get[Int]("research_guide_id") map {
      case pid ~ layout ~ name ~ path ~ menu ~ query ~ id  =>
        GuidePage(pid, layout, name, path, menu, query, id)
    }
  }

  /*
  * Create a new page
  */
  def create(layout: String, name: String, path: String, menu: String, cypher: String, parent: Option[Int]): Option[Long] = DB.withConnection {
    implicit connection =>
      SQL(
        """INSERT INTO research_guide_page (layout, name, path, menu, cypher, research_guide_id)
           VALUES ({l}, {n}, {p}, {m}, {c}, {parent})""")
        .on('l -> layout, 'n -> name, 'p -> path, 'm -> menu, 'c -> cypher, 'parent -> parent).executeInsert()
  }

  /*
  * List or find data
  */
  def findAll(path: String): List[GuidePage] = DB.withConnection {
    implicit connection =>
      SQL( """SELECT * FROM research_guide_page WHERE path = {path}""")
        .on('path -> path).as(rowExtractor *)
  }

  def faceted: GuidePage = {
    GuidePage(
      None,
      "facet",
      "portal.guides.faceted",
      "browse",
      "top",
      "",
      0
    )
  }
}


