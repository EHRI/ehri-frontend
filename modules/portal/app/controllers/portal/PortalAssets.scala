package controllers.portal

import play.api.http.HttpErrorHandler
import javax.inject._

import controllers.AssetsMetadata


@Singleton
case class PortalAssets @Inject()(errorHandler: HttpErrorHandler, meta: AssetsMetadata) extends controllers.AssetsBuilder(errorHandler, meta)

