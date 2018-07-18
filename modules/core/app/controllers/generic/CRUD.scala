package controllers.generic

import models.base.{Model, ModelData, Persistable}


trait CRUD[T <: Model{type T <: ModelData with Persistable}]
		extends Create[T]
		with Read[T]
		with Update[T]
		with Delete[T]
