package controllers.portal

import play.api.http.HttpErrorHandler

import javax.inject._
import controllers.AssetsMetadata
import play.api.Environment


@Singleton
case class PortalAssets @Inject()(errorHandler: HttpErrorHandler, meta: AssetsMetadata, env: Environment) extends controllers.AssetsBuilder(errorHandler, meta, env)

