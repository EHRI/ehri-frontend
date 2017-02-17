package controllers.admin

import play.api.http.HttpErrorHandler
import javax.inject.{Inject, Singleton}

import controllers.AssetsMetadata

@Singleton
case class AdminAssets @Inject()(errorHandler: HttpErrorHandler, meta: AssetsMetadata) extends controllers.AssetsBuilder(errorHandler, meta)


