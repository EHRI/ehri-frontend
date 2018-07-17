package controllers.generic

import models.base.{MetaModel, Model, Persistable}


trait CRUD[T <: MetaModel{type T <: Model with Persistable}]
		extends Create[T]
		with Read[T]
		with Update[T]
		with Delete[T]
