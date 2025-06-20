package controllers.portal.guides

import javax.inject.{Inject, Singleton}
import controllers.AssetsMetadata
import play.api.Environment
import play.api.http.HttpErrorHandler

@Singleton
case class GuideAssets @Inject()(errorHandler: HttpErrorHandler, meta: AssetsMetadata, environment: Environment) extends controllers.AssetsBuilder(errorHandler, meta, environment)
