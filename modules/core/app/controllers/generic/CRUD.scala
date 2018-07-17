package controllers.generic

import models.base.{MetaModel, Model, Persistable}


trait CRUD[F <: Model with Persistable, T <: MetaModel[F]]
		extends Create[F,T]
		with Read[T]
		with Update[F,T]
		with Delete[F, T]
