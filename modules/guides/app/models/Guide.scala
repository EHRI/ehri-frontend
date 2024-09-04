package models

import javax.inject.{Singleton, Inject}

import models.GuidePage.{MenuPosition, Layout}
import play.api.data.Form
import play.api.data.Forms._

import anorm._
import anorm.SqlParser._
import play.api.db.Database
import language.postfixOps
import scala.util.Try
import models.sql.withIntegrityCheck


case class Guide(
  id: Option[Long] = None,
  name: String,
  path: String,
  picture: Option[String] = None,
  virtualUnit: String,
  description: Option[String] = None,
  css: Option[String] = None,
  active: Boolean = true,
  defaultPage: Option[Long] = None
)

object Guide {
  val PREFIX = "guide"
  val OBJECTID = "id"
  val PATH = "path"
  val NAME = "name"
  val VIRTUALUNIT = "virtual_unit"
  val DESCRIPTION = "description"
  val PICTURE = "picture"
  val ACTIVE = "active"
  val DEFAULT_PAGE = "default_page"
  val CSS = "css"

  implicit val form: Form[models.Guide] = Form(
    mapping(
      OBJECTID -> ignored(Option.empty[Long]),
      NAME -> nonEmptyText,
      PATH -> nonEmptyText.verifying("guide.path.constraint.validity",
        p => p.matches("[0-9a-zA-Z\\-]+")),
      PICTURE -> optional(nonEmptyText),
      VIRTUALUNIT -> nonEmptyText,
      DESCRIPTION -> optional(text),
      CSS -> optional(text),
      ACTIVE -> boolean,
      DEFAULT_PAGE -> optional(longNumber)
    )(Guide.apply)(Guide.unapply)
  )

  private[models] val rowExtractor: RowParser[Guide] = {
    get[Option[Long]](OBJECTID) ~
      get[String](NAME) ~
      get[String](PATH) ~
      get[Option[String]](PICTURE) ~
      get[String](VIRTUALUNIT) ~
      get[Option[String]](DESCRIPTION) ~
      get[Option[String]](CSS) ~
      get[Boolean](ACTIVE) ~
      get[Option[Long]](DEFAULT_PAGE) map {
      case gid ~ name ~ path ~ pic ~ virtualUnit ~ desc ~ css ~ active ~ deft =>
        Guide(gid, name, path, pic, virtualUnit, desc, css, active, deft)
    }
  }

  def blueprint(): Guide = Guide(Some(0), "", "", Some(""), "", Some(""), Some(""), active = true)
}

trait GuideService {
  def findPages(guide: Guide): List[GuidePage]
  def findPage(guide: Guide, path: String): Option[GuidePage]
  def getDefaultPage(guide: Guide): Option[GuidePage]
  def delete(guide: Guide): Unit
  def update(guide: Guide): Try[Unit]
  def create(name: String, path: String, picture: Option[String] = None, virtualUnit: String, description: Option[String] = None, css: Option[String] = None, active: Boolean): Try[Option[Guide]]
  def findAll(activeOnly: Boolean = false): List[Guide]
  def find(path: String, activeOnly: Boolean = false): Option[Guide]
  def findById(id: Long): Option[Guide]

  // Pages
  def updatePage(page: GuidePage): Try[Unit]
  def deletePage(page: GuidePage): Unit
  def createPage(layout: Layout.Value, name: String, path: String, menu: MenuPosition.Value = MenuPosition.Side,
                 cypher: String, parent: Option[Long] = None, description: Option[String] = None, params: Option[String] = None): Try[Option[GuidePage]]
  def findPage(path: String): List[GuidePage]
  def findAllPages(): List[GuidePage]
}

@Singleton
case class SqlGuideService @Inject()()(implicit db: Database) extends GuideService {

  override def findPages(guide: Guide): List[GuidePage] = db.withConnection { implicit connection =>
    guide.id.map { id =>
      SQL"""SELECT * FROM research_guide_page WHERE research_guide_id = $id"""
        .as(GuidePage.rowExtractor *)
    }.toList.flatten
  }

  override def findPage(guide: Guide, path: String): Option[GuidePage] = db.withConnection { implicit connection =>
    guide.id.flatMap { id =>
      SQL"""SELECT * FROM research_guide_page WHERE research_guide_id = ${guide.id} AND path = $path"""
        .as(GuidePage.rowExtractor.singleOpt)
    }
  }

  override def update(guide: Guide): Try[Unit] = withIntegrityCheck { implicit connection =>
    SQL"""
      UPDATE
        research_guide
      SET
        name = ${guide.name},
        path = ${guide.path},
        picture = ${guide.picture},
        virtual_unit = ${guide.virtualUnit},
        description = ${guide.description},
        css = ${guide.css},
        active = ${guide.active},
        default_page = ${guide.defaultPage}
      WHERE
        id = ${guide.id}
      """.executeUpdate()
  }

  def getDefaultPage(guide: Guide): Option[GuidePage] = db.withConnection { implicit connection =>
    val page = for {
      id <- guide.id
      dp <- guide.defaultPage
      page <- SQL"""SELECT * FROM research_guide_page WHERE research_guide_id = $id AND id = $dp"""
        .as(GuidePage.rowExtractor.singleOpt)
    } yield page
    page.orElse(findPages(guide).headOption)
  }

  override def findById(id: Long): Option[Guide] = db.withConnection { implicit connection =>
    SQL"""SELECT * FROM research_guide WHERE id = $id""".as(Guide.rowExtractor.singleOpt)
  }

  override def findAll(activeOnly: Boolean = false): List[Guide] = db.withConnection { implicit connection =>
    (if (activeOnly) SQL"""SELECT * FROM research_guide WHERE active"""
    else SQL"""SELECT * FROM research_guide"""
      ).as(Guide.rowExtractor *)
  }

  override def delete(guide: Guide): Unit = db.withConnection { implicit connection =>
    SQL"""DELETE FROM research_guide WHERE id = ${guide.id}""".executeUpdate()
  }

  override def find(path: String, activeOnly: Boolean = false): Option[Guide] = db.withConnection { implicit connection =>
    (if (activeOnly) SQL"""SELECT * FROM research_guide WHERE path = $path AND active LIMIT 1"""
    else SQL"""SELECT * FROM research_guide WHERE path = $path LIMIT 1""")
      .as(Guide.rowExtractor.singleOpt)
  }

  override def create(name: String, path: String, picture: Option[String] = None, virtualUnit: String, description: Option[String] = None, css: Option[String] = None, active: Boolean): Try[Option[Guide]] = withIntegrityCheck { implicit connection =>
    val id: Option[Long] = SQL"""
    INSERT INTO research_guide
      (name, path, picture, virtual_unit, description, css, active)
    VALUES ($name, $path, $picture, $virtualUnit, $description, $css, $active)
    """.executeInsert(SqlParser.scalar[Long].singleOpt)
    id.flatMap(findById)
  }

  /*
  * Edit a page
  */
  override def updatePage(page: GuidePage): Try[Unit] = withIntegrityCheck { implicit connection =>
    SQL"""
      UPDATE
        research_guide_page
      SET
        layout = ${page.layout},
        name = ${page.name},
        path = ${page.path},
        position = ${page.position},
        content = ${page.content},
        research_guide_id = ${page.parent},
        params = ${page.params},
        description = ${page.description}
      WHERE id = ${page.id}
    """.executeUpdate()
  }

  /*
  * Delete a page
  */
  override def deletePage(page: GuidePage): Unit = db.withConnection { implicit connection =>
    SQL"""DELETE FROM research_guide_page WHERE id = ${page.id}""".executeUpdate()
  }

  /*
  * Create a new page
  */
  override def createPage(layout: Layout.Value, name: String, path: String, menu: MenuPosition.Value = MenuPosition.Side,
             cypher: String, parent: Option[Long] = None, description: Option[String] = None, params: Option[String] = None): Try[Option[GuidePage]] =
    withIntegrityCheck { implicit connection =>
      val id: Option[Long] = SQL"""
      INSERT INTO research_guide_page
        (layout, name, path, position, content, research_guide_id, description, params)
      VALUES
        ($layout, $name, $path, $menu, $cypher, $parent, $description, $params)
    """.executeInsert(SqlParser.scalar[Long].singleOpt)
      id.flatMap { l =>
        SQL"SELECT * FROM research_guide_page WHERE id = $l".as(GuidePage.rowExtractor.singleOpt)
      }
    }

  /*
  * List or find data
  */
  override def findPage(path: String): List[GuidePage] = db.withConnection { implicit connection =>
    SQL"""SELECT * FROM research_guide_page WHERE path = $path""".as(GuidePage.rowExtractor *)
  }

  override def findAllPages(): List[GuidePage] = db.withConnection { implicit connection =>
    SQL"""SELECT * FROM research_guide_page""".as(GuidePage.rowExtractor *)
  }
}


