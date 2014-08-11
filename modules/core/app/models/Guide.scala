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
case class Guide(
  id: Option[Long] = None,
  name: String,
  path: String,
  picture: Option[String] = None,
  virtualUnit: String,
  description: Option[String] = None,
  active: Int = 0,
  default: Long = 0
) {
  def update(): Unit = DB.withConnection { implicit connection =>
    SQL(
      """
    UPDATE
      research_guide
    SET
      name = {n},
      path = {p},
      picture = {pi},
      virtual_unit = {vu},
      description = {de},
      active = {active},
      `default` = {default}
    WHERE
      id = {i}
    LIMIT 1
      """
    ).on('n -> name, 'p -> path, 'vu -> virtualUnit, 'pi -> picture, 'de -> description, 'i -> id, 'active -> active, 'default -> default)
      .executeUpdate()
  }

  def delete(): Unit = DB.withConnection { implicit connection =>
    SQL("""DELETE FROM research_guide WHERE id = {i} LIMIT 1""")
      .on('i -> id).executeUpdate()
  }
  
  def findPages(): List[GuidePage] = DB.withConnection { implicit connection =>
    id.map { id =>
        SQL( """SELECT * FROM research_guide_page WHERE research_guide_id = {id}""")
          .on('id -> id).as(GuidePage.rowExtractor *)
    }.toList.flatten
  }

  def findPage(path: String): Option[GuidePage] = DB.withConnection { implicit connection =>
    id.flatMap { id =>
      SQL( """SELECT * FROM research_guide_page WHERE research_guide_id = {id} AND path = {path}""")
        .on('id -> id, 'path -> path).as(GuidePage.rowExtractor.singleOpt)
    }
  }

  def getDefaultPage: Option[GuidePage] = DB.withConnection { implicit connection =>
    id.flatMap { id =>
      SQL( """SELECT * FROM research_guide_page WHERE research_guide_id = {id} AND id = {gid}""")
        .on('id -> id, 'gid -> default).as(GuidePage.rowExtractor.singleOpt)
    }
  }
}

object Guide {
  val PREFIX = "guide"
  val OBJECTID = "id"
  val PATH = "path"
  val NAME = "name"
  val VIRTUALUNIT = "virtual_unit"
  val DESCRIPTION = "description"
  val PICTURE = "picture"
  val ACTIVE = "active"
  val DEFAULT = "default"

  implicit val form = Form(
    mapping(
      OBJECTID -> ignored(Option.empty[Long]),
      NAME -> nonEmptyText,
      PATH -> nonEmptyText,
      PICTURE -> optional(nonEmptyText),
      VIRTUALUNIT -> nonEmptyText,
      DESCRIPTION -> optional(text),
      // FIXME: Active really shouldn't be an int
      ACTIVE -> optional(boolean).transform[Int](f => if(f.getOrElse(false)) 1 else 0, i => Some(i > 0)),
      DEFAULT -> longNumber
    )(Guide.apply)(Guide.unapply)
  )

  val rowExtractor = {
    get[Option[Long]](OBJECTID) ~
      get[String](NAME) ~
      get[String](PATH) ~
      get[Option[String]](PICTURE) ~
      get[String](VIRTUALUNIT) ~
      get[Option[String]](DESCRIPTION) ~
      get[Int](ACTIVE) ~
      get[Long](DEFAULT) map {
      case gid ~ name ~ path ~ pic ~ virtualUnit ~ desc ~ active ~ deft =>
        Guide(gid, name, path, pic, virtualUnit, desc, active, deft)
    }
  }

  def blueprint(): Guide = Guide(Some(0), "", "", Some(""), "", Some(""), active = 0, 0)

  /*
  *   Create function
  */
  def create(name: String, path: String, picture: Option[String] = None, virtualUnit: String, description: Option[String] = None, active: Int): Option[Guide] = DB.withConnection { implicit connection =>
    val id: Option[Long] = SQL("""
      INSERT INTO research_guide
        (name, path, picture, virtual_unit, description, active) VALUES ({n}, {p}, {pi}, {vu}, {de}, {a})""")
      .on('n -> name, 'p -> path, 'pi -> picture, 'vu -> virtualUnit, 'de -> description, 'a -> active).executeInsert()
    id.flatMap(findById)
  }

  /*
  *   Listing functions
  */
  def findAll(activeOnly: Boolean = false): List[Guide] = DB.withConnection { implicit connection =>
    SQL(
      if (activeOnly) """SELECT * FROM research_guide WHERE active = 1"""
      else """SELECT * FROM research_guide"""
    ).as(rowExtractor *)
  }

  def find(param: String, activeOnly: Boolean = false): Option[Guide] = DB.withConnection { implicit connection =>
    (if (activeOnly) SQL("""SELECT * FROM research_guide WHERE path = {param} AND active = 1 LIMIT 1""")
      else SQL("""SELECT * FROM research_guide WHERE path = {param} LIMIT 1""")
    ).on('param -> param).as(rowExtractor.singleOpt)
  }

  def findById(id: Long): Option[Guide] = DB.withConnection { implicit connection =>
    SQL("""SELECT * FROM research_guide WHERE id = {id}""")
      .on('id -> id).as(rowExtractor.singleOpt)
  }

}


