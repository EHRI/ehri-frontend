package controllers.generic

import backend.Backend
import controllers.base.AuthController

trait Generic[MT] extends AuthController {
  val backend: Backend
}
