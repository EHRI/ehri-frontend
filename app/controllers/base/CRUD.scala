package controllers.base

import models.base.AccessibleEntity
import models.base.Formable
import models.base.Persistable


trait CRUD[F <: Persistable, T <: AccessibleEntity with Formable[F]]
		extends EntityCreate[F,T] 
		with EntityRead[T]
		with EntityUpdate[F,T]
		with EntityDelete[T]
