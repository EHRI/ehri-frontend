package models

import play.api.data.Form
import play.api.data.Forms._

import anorm._
import anorm.SqlParser._
import anorm.~
import play.api.Play.current
import play.api.db.DB


/**
 * @author Thibault Clérice (http://github.com/ponteineptique)
 */
case class GuidesPage(
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
  def update(): Int = DB.withConnection { implicit connection =>
    SQL(
      """

      UPDATE
        research_guide_page
      SET
        layout_research_guide_page = {l}, 
        name_research_guide_page = {n}, 
        path_research_guide_page = {p}, 
        menu_research_guide_page = {m}, 
        cypher_research_guide_page = {c}, 
        id_research_guide = {parent}
      WHERE
        id_research_guide_page = {id}
      LIMIT 1
      """
    ).on('l -> layout, 'n -> name, 'p -> path, 'm -> position, 'c -> content, 'parent -> parent, 'id -> objectId).executeUpdate()
  }

  /*
  * Delete a page
  */
  def delete(): Int = DB.withConnection { implicit connection =>
    SQL(
      """
      DELETE FROM
        research_guide_page
      WHERE 
        id_research_guide_page = {i}
      LIMIT 1
      """
    ).on('i -> objectId).executeUpdate()
  }
}

object GuidesPage {

  val PREFIX = "guides.page"
  val OBJECTID = "objectId"
  val PATH = "path"
  val NAME = "name"
  val LAYOUT = "layout"
  val POSITION = "position"
  val CONTENT = "content"
  val PARENT = "parent"
  
  val layouts: List[String] = List(
    "md", 
    "organisation" , 
    "person" , 
    "map" , 
    "keyword"
  )

  val positions: List[String] = List(
    "top" , 
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
    )(GuidesPage.apply _)(GuidesPage.unapply _)
  )

  def blueprint(guideId: Option[Int]): GuidesPage = {
    GuidesPage(None, "", "", "", "", "", guideId.getOrElse(0))
  }

/*
* Create a new page
*/
def create(layout: String, name: String, path: String, menu: String, cypher: String, parent: Option[Int]): Option[Long] = DB.withConnection { implicit connection =>
  SQL(
    """

    INSERT INTO
      research_guide_page
    (
      layout_research_guide_page, 
      name_research_guide_page, 
      path_research_guide_page, 
      menu_research_guide_page, 
      cypher_research_guide_page, 
      id_research_guide
    )
    VALUES
    ({l}, {n}, {p}, {m}, {c}, {parent})
    """
  ).on('l -> layout, 'n -> name, 'p -> path, 'm -> menu, 'c -> cypher, 'parent -> parent).executeInsert()
}

/*
* List or find data
*/
  def findAll(param: Any):List[GuidesPage] = DB.withConnection { implicit connection =>
    SQL(
      param match {
        case p:String => { """
        SELECT 
          rgp.*
        FROM 
          research_guide_page rgp,
          research_guide rg 
        WHERE 
          rg.id_research_guide = rgp.id_research_guide AND
          rg.path_research_guide = {param}
        """}
        case p:Option[Int] => { """
          SELECT 
            *
          FROM
            research_guide_page 
          WHERE 
            id_research_guide = {param}
        """}
      }
      
    ).on('param -> param).apply().map { row =>
      GuidesPage(
        row[Option[Int]]("id_research_guide_page"),
        row[String]("layout_research_guide_page"),
        row[String]("name_research_guide_page"),
        row[String]("path_research_guide_page"),
        row[String]("menu_research_guide_page"),
        row[String]("cypher_research_guide_page"),
        row[Int]("id_research_guide")
      )
    }.toList
  }

  def find(guide:Option[Int], key:Any): Option[GuidesPage] = DB.withConnection { implicit connection =>
      SQL(
        key match {
          case param:String => 
          """
            SELECT 
              *
            FROM 
              research_guide_page
            WHERE 
              id_research_guide = {guide} AND
              path_research_guide_page = {param}
            LIMIT 1
          """ 
          case p:Int => """
            SELECT 
              *
            FROM 
              research_guide_page
            WHERE
              id_research_guide = {guide} AND
              id_research_guide_page = {param}
            LIMIT 1
          """
        }
      ).on('param -> key, 'guide -> guide).apply().headOption.map { row =>
        GuidesPage(
          row[Option[Int]]("id_research_guide_page"),
          row[String]("layout_research_guide_page"),
          row[String]("name_research_guide_page"),
          row[String]("path_research_guide_page"),
          row[String]("menu_research_guide_page"),
          row[String]("cypher_research_guide_page"),
          row[Int]("id_research_guide")
        )
      }
    }

  def faceted: GuidesPage = {
    GuidesPage(
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


