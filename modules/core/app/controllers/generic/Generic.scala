package controllers.generic

import backend.Backend
import controllers.base.AuthController

trait Generic[MT] extends AuthController {
  //implicit val resource: RestResource[MT]
  val backend: Backend
}
