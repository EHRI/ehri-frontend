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
case class Guide(
  objectId: Option[Int],
  name: String,
  path: String,
  picture: Option[String],
  description: Option[String],
  active: Int = 0,
  default: Int = 0
) {
  def update(): Int = DB.withConnection { implicit connection =>
    SQL(
      """
      UPDATE
        research_guide
      SET 
        name_research_guide = {n},
        path_research_guide = {p},
        picture_research_guide = {pi},
        description_research_guide = {de},
        active_research_guide = {active},
        default_research_guide = {default}
      WHERE 
        id_research_guide = {i}
      LIMIT 1
      """
    ).on('n -> name, 'p -> path, 'pi -> picture, 'de -> description, 'i -> objectId, 'active -> active, 'default -> default).executeUpdate()
  }

  def delete(): Int = DB.withConnection { implicit connection =>
    SQL(
      """
      DELETE FROM
        research_guide
      WHERE 
        id_research_guide = {i}
      LIMIT 1
      """
    ).on('i -> objectId).executeUpdate()
  }
}

object Guide {
  val PREFIX = "guides"
  val OBJECTID = "objectId"
  val PATH = "path"
  val NAME = "name"
  val DESCRIPTION = "description"
  val PICTURE = "picture"
  val ACTIVE = "active"
  val DEFAULT = "default"

  implicit val form = Form(
    mapping(
      "objectId" -> optional(number),
      "name" -> text,
      "path" -> text,
      "picture" -> optional(nonEmptyText),
      "description" -> optional(text),
      "active" -> number,
      "default" -> number
    )(Guide.apply _)(Guide.unapply _)
  )
  /*
  *   Create function
  */
  def create(name: String, path: String, picture: Option[String], description: Option[String]): Option[Long] = DB.withConnection { implicit connection =>
    SQL(
      """
      INSERT INTO
        research_guide
      (name_research_guide,path_research_guide, picture_research_guide, description_research_guide)
      VALUES
      ({n}, {p}, {pi}, {de})
      """
    ).on('n -> name, 'p -> path, 'pi -> picture, 'de -> description).executeInsert()
  }

  /*
  *   Listing functions
  */
  def findAll(active:Boolean = false): List[Guide] = DB.withConnection { implicit connection =>
    SQL(
    if (active) """
      SELECT * FROM research_guide WHERE active_research_guide = 1
    """ else """
      SELECT * FROM research_guide
    """
    ).apply().map { row =>
      Guide(
      row[Option[Int]]("id_research_guide"),
      row[String]("name_research_guide"),
      row[String]("path_research_guide"),
      row[Option[String]]("picture_research_guide"),
      row[Option[String]]("description_research_guide"),
      row[Int]("active_research_guide"),
      row[Int]("default_research_guide")
      )
    }.toList
  }

  def find(param: Any, active:Int = 0): Option[Guide] = DB.withConnection { implicit connection =>
    SQL(
      param match {
        case p:String => 
          if(active==1) """
          SELECT 
            * 
          FROM 
            research_guide
          WHERE 
            path_research_guide = {param} AND
            active_research_guide = 1
          LIMIT 1
        """ else """
          SELECT 
            * 
          FROM 
            research_guide
          WHERE 
            path_research_guide = {param}
          LIMIT 1
        """
        case p:Int => if(active == 1) """
          SELECT 
            * 
          FROM 
            research_guide
          WHERE 
            id_research_guide = {param} AND
            active_research_guide = 1
          LIMIT 1
        """ else """
          SELECT 
            * 
          FROM 
            research_guide
          WHERE 
            id_research_guide = {param}
          LIMIT 1
        """ 
      }
    ).on('param -> param).apply().headOption.map { row =>
      Guide(
        row[Option[Int]]("id_research_guide"),
        row[String]("name_research_guide"),
        row[String]("path_research_guide"),
        row[Option[String]]("picture_research_guide"),
        row[Option[String]]("description_research_guide"),
        row[Int]("active_research_guide"),
        row[Int]("default_research_guide")
      )
    }
  }
}


