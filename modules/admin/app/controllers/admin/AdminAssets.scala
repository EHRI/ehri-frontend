package controllers.admin

import play.api.http.HttpErrorHandler
import javax.inject.{Inject, Singleton}

@Singleton
case class AdminAssets @Inject()(errorHandler: HttpErrorHandler) extends controllers.AssetsBuilder(errorHandler)


