package controllers.api

import javax.inject.{Inject, Singleton}

import auth.AccountManager
import backend.DataApi
import controllers.portal.base.PortalController
import defines.EntityType
import models.api.v1.JsonApiV1._
import play.api.cache.CacheApi
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json._
import play.api.mvc._
import utils.MovedPageLookup

import scala.concurrent.ExecutionContext


@Singleton
case class ApiHome @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  executionContext: ExecutionContext
) extends PortalController {

  def index = OptionalUserAction { implicit request =>
    Ok(views.html.api.docs())
  }
}
