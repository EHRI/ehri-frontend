package controllers.portal

import play.api.http.HttpErrorHandler
import javax.inject._


@Singleton
case class PortalAssets @Inject()(errorHandler: HttpErrorHandler) extends controllers.AssetsBuilder(errorHandler)

