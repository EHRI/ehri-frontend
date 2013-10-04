package controllers.portal

import play.api.Play.current
import _root_.models.{IsadG, UserProfile}
import controllers.base.EntitySearch
import models.base.AnyModel
import play.api._
import play.api.mvc._
import views.html._
import play.api.http.MimeTypes

import com.google.inject._
import utils.search.{Dispatcher, SearchOrder, SearchParams}
import play.api.libs.json.{Format, Writes, Json}
import solr.facet.FieldFacetClass
import play.api.i18n.Messages
import views.Helpers
import play.api.cache.Cached
import solr.facet.FieldFacetClass
import play.api.i18n.Messages
import views.Helpers


@Singleton
class Portal @Inject()(implicit val globalConfig: global.GlobalConfig, val searchDispatcher: Dispatcher) extends Controller with EntitySearch {

  val entityFacets = List(
    FieldFacetClass(
      key=IsadG.LANG_CODE,
      name=Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
      param="lang",
      render=Helpers.languageCodeToName
    ),
    FieldFacetClass(
      key="type",
      name=Messages("search.type"),
      param="type",
      render=s => Messages("contentTypes." + s)
    ),
    FieldFacetClass(
      key="copyrightStatus",
      name=Messages("copyrightStatus.copyright"),
      param="copyright",
      render=s => Messages("copyrightStatus." + s)
    ),
    FieldFacetClass(
      key="scope",
      name=Messages("scope.scope"),
      param="scope",
      render=s => Messages("scope." + s)
    )
  )

  // This is a publically-accessible site
  override val staffOnly = false

  // i.e. Everything
  private val entityFacets = List(
    FieldFacetClass(
      key = IsadG.LANG_CODE,
      name = Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
      param = "lang",
      render = Helpers.languageCodeToName
    ),
    FieldFacetClass(
      key = "type",
      name = Messages("search.type"),
      param = "type",
      render = s => Messages("contentTypes." + s)
    ),
    FieldFacetClass(
      key = "copyrightStatus",
      name = Messages("copyrightStatus.copyright"),
      param = "copyright",
      render = s => Messages("copyrightStatus." + s)
    ),
    FieldFacetClass(
      key = "scope",
      name = Messages("scope.scope"),
      param = "scope",
      render = s => Messages("scope." + s)
    )
  )


  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads

  def search = searchAction[AnyModel](
    defaultParams = Some(SearchParams(sort = Some(SearchOrder.Score))),
    entityFacets = entityFacets) {
    page => params => facets => implicit userOpt => implicit request =>
      render {
        case Accepts.Json() => {
          Ok(Json.toJson(Json.obj(
            "numPages" -> page.numPages,
            "page" -> Json.toJson(page.items.map(_._1))(Writes.seq(AnyModel.Converter.clientFormat)),
            "facets" -> facets
          ))
          )
        }
        case _ => Ok(views.html.search.search(page, params, facets,
          controllers.portal.routes.Portal.search))
      }
  }


  /**
   * Quick filter action that searches applies a 'q' string filter to
   * only the name_ngram field and returns an id/name pair.
   * @return
   */
  def filter = filterAction() { page => implicit userOpt => implicit request =>
    Ok(Json.obj(
      "numPages" -> page.numPages,
      "page" -> page.page,
      "items" -> page.items.map { case (id, name, t) =>
        Json.arr(id, name, t.toString)
      }
    ))
  }




  def jsRoutes = Action { implicit request =>

    import controllers.core.routes.javascript._
    import controllers.archdesc.routes.javascript.DocumentaryUnits
    import controllers.archdesc.routes.javascript.Countries
    import controllers.archdesc.routes.javascript.Repositories
    import controllers.vocabs.routes.javascript.Vocabularies
    import controllers.vocabs.routes.javascript.Concepts
    import controllers.authorities.routes.javascript.AuthoritativeSets
    import controllers.authorities.routes.javascript.HistoricalAgents

    Ok(
      Routes.javascriptRouter("jsRoutes")(
        controllers.portal.routes.javascript.Portal.account,
        controllers.portal.routes.javascript.Portal.profile,
        controllers.portal.routes.javascript.Portal.search,
        controllers.portal.routes.javascript.Portal.filter,
        Application.getType,
        Application.getGeneric,
        UserProfiles.list,
        UserProfiles.get,
        Groups.list,
        Groups.get,
        DocumentaryUnits.search,
        DocumentaryUnits.get,
        Countries.search,
        Countries.get,
        Repositories.search,
        Repositories.get,
        Vocabularies.list,
        Vocabularies.get,
        Concepts.search,
        Concepts.get,
        AuthoritativeSets.list,
        AuthoritativeSets.get,
        HistoricalAgents.search,
        HistoricalAgents.get
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

  /**
   * Render entity types into the view to serve as JS constants.
   * @return
   */
  def globalData = Action { implicit request =>
    import defines.EntityType
    Ok(
      """
        |var EntityTypes = {
        |%s
        |};
      """.stripMargin.format(
        "\t" + EntityType.values.map(et => s"$et: '$et'").mkString(",\n\t"))
    ).as(MimeTypes.JAVASCRIPT)
  }

  def account = userProfileAction { implicit userOpt => implicit request =>
    Ok(Json.toJson(userOpt.flatMap(_.account)))
  }

  def profile = userProfileAction { implicit userOpt => implicit request =>
    Ok(Json.toJson(userOpt)(Format.optionWithNull(UserProfile.Converter.clientFormat)))
  }

  def index = userProfileAction { implicit userOpt => implicit request =>
    Ok(views.html.portal())
  }

  def placeholder = Cached("pages:portalPlaceholder") {
    Action { implicit request =>
      Ok(views.html.placeholder())
    }
  }
}

