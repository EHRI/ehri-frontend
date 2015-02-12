package controllers.generic

import backend.Backend
import controllers.base.CoreActionBuilders

trait Generic[MT] extends CoreActionBuilders {
  val backend: Backend
}
