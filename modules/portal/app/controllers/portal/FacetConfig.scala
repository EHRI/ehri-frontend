package controllers.portal

import solr.facet.{SolrQueryFacet, QueryFacetClass, FieldFacetClass}
import models.{Isaar, IsadG}
import play.api.i18n.Messages
import views.Helpers
import utils.search.{Facet, FacetClass, FacetSort, FacetDisplay}
import solr.SolrConstants
import defines.EntityType
import controllers.generic.Search

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait FacetConfig extends Search {

  // i.e. Everything
  protected val globalSearchFacets: FacetBuilder = { implicit lang =>
    List(
      FieldFacetClass(
        key = IsadG.LANG_CODE,
        name = Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = "type",
        name = Messages("search.type"),
        param = "type",
        render = s => Messages("contentTypes." + s),
        display = FacetDisplay.Choice
      )
    )
  }

  // i.e. Metrics to count number and type of repos, docs, authorities
  // for display on the landing page.
  protected val entityMetrics: FacetBuilder = { implicit lang =>
    List(
      FieldFacetClass(
        key = SolrConstants.TYPE,
        name = Messages("search.type"),
        param = SolrConstants.TYPE,
        render = s => Messages("contentTypes." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key=Isaar.ENTITY_TYPE,
        name=Messages(Isaar.FIELD_PREFIX + "." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages(Isaar.FIELD_PREFIX + "." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("isdiah.countryCode"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="holderName",
        name=Messages("documentaryUnit.heldBy"),
        param="holder",
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }

  protected val historicalAgentFacets: FacetBuilder = { implicit lang =>
    List(
      FieldFacetClass(
        key=Isaar.ENTITY_TYPE,
        name=Messages(Isaar.FIELD_PREFIX + "." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages(Isaar.FIELD_PREFIX + "." + s),
        display = FacetDisplay.Choice
      )
    )
  }

  protected val countryFacets: FacetBuilder = { implicit lang =>
    List() // TODO?
  }

  protected val repositorySearchFacets: FacetBuilder = { implicit lang =>
    List(
      QueryFacetClass(
        key="childCount",
        name=Messages("portal.facet.itemsHeldOnline"),
        param="docs",
        render=s => Messages("portal.facet.itemsHeldOnline." + s),
        facets=List(
          SolrQueryFacet(value = "yes", solrValue = "[1 TO *]", name = Some("yes"))
        ),
        display = FacetDisplay.Boolean
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("isdiah.countryCode"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }

  protected val docSearchFacets: FacetBuilder = { implicit lang =>
    List(
      FieldFacetClass(
        key = IsadG.LANG_CODE,
        name = Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      QueryFacetClass(
        key="childCount",
        name=Messages("portal.facet.container"),
        param="items",
        render=s => Messages("portal.facet.container." + s),
        facets=List(
          SolrQueryFacet(value = "true", solrValue = "[1 TO *]", name = Some("hasChildItems"))
        ),
        display = FacetDisplay.Boolean
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("portal.facet.location"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="accessPoints",
        name=Messages("Keyword"),
        param="kw",
        render= s => s,
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }

  protected val docSearchRepositoryFacets: FacetBuilder = { implicit lang =>
    docSearchFacets(lang) ++ List(
      FieldFacetClass(
        key="holderName",
        name=Messages("documentaryUnit.heldBy"),
        param="holder",
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }


}
