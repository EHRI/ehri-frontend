package backend

import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Descriptions {
  def createDescription[MT, DT](id: String, item: DT, logMsg: Option[String] = None)(implicit rs: Resource[MT], fmt: Writable[DT], rd: Readable[DT]): Future[DT]

  def updateDescription[MT, DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit rs: Resource[MT], fmt: Writable[DT], rd: Readable[DT]): Future[DT]

  def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(implicit rs: Resource[MT]): Future[Unit]

  def createAccessPoint[MT, DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit rs: Resource[MT], fmt: Writable[DT]): Future[DT]

  def deleteAccessPoint(id: String, did: String, apid: String, logMsg: Option[String] = None): Future[Unit]
}
