package controllers.base

import models.base.{MetaModel, Model, Persistable}


trait CRUD[F <: Model with Persistable, T <: MetaModel[F]]
		extends EntityCreate[F,T] 
		with EntityRead[T]
		with EntityUpdate[F,T]
		with EntityDelete[T]
