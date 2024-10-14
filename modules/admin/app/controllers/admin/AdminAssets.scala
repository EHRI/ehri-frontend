package controllers.admin

import play.api.http.HttpErrorHandler

import javax.inject.{Inject, Singleton}
import controllers.AssetsMetadata
import play.api.Environment

@Singleton
case class AdminAssets @Inject()(errorHandler: HttpErrorHandler, meta: AssetsMetadata, env: Environment) extends controllers.AssetsBuilder(errorHandler, meta, env)


