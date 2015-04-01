package backend

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Descriptions {
  def createDescription[MT, DT](id: String, item: DT, logMsg: Option[String] = None)(implicit rs: BackendResource[MT], fmt: BackendWriteable[DT], rd: BackendReadable[DT], executionContext: ExecutionContext): Future[DT]

  def updateDescription[MT, DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit rs: BackendResource[MT], fmt: BackendWriteable[DT], rd: BackendReadable[DT], executionContext: ExecutionContext): Future[DT]

  def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(implicit rs: BackendResource[MT], executionContext: ExecutionContext): Future[Unit]

  def createAccessPoint[MT, DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit rs: BackendResource[MT], fmt: BackendWriteable[DT], executionContext: ExecutionContext): Future[DT]

  def deleteAccessPoint(id: String, did: String, apid: String, logMsg: Option[String] = None)(implicit executionContext: ExecutionContext): Future[Unit]
}
