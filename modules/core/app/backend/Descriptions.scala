package backend

import scala.concurrent.{ExecutionContext, Future}
import models.base.AnyModel

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
trait Descriptions {
   def createDescription[MT,DT](id: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: BackendResource[MT], fmt: BackendWriteable[DT], rd: BackendReadable[MT], executionContext: ExecutionContext): Future[MT]
   def updateDescription[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: BackendResource[MT], fmt: BackendWriteable[DT], rd: BackendReadable[MT], executionContext: ExecutionContext): Future[MT]
   def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: BackendResource[MT], rd: BackendReadable[MT], executionContext: ExecutionContext): Future[Boolean]
   def createAccessPoint[DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, fmt: BackendWriteable[DT], executionContext: ExecutionContext): Future[DT]
   def deleteAccessPoint(id: String, did: String, apid: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean]
}
