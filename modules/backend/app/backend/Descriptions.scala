package backend

import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Descriptions {
  /**
   * Create a new description on a given item.
   *
   * @param id the item's ID
   * @param desc the description data
   * @param logMsg an optional log message
   * @tparam MT the item's meta type
   * @tparam DT the item's description type
   */
  def createDescription[MT: Resource, DT: Writable](id: String, desc: DT, logMsg: Option[String] = None): Future[DT]

  /**
   * Update a description on a given item.
   *
   * @param id the item's ID
   * @param did the description ID           
   * @param desc the description data
   * @param logMsg an optional log message
   * @tparam MT the item's meta type
   * @tparam DT the item's description type
   * @return
   */
  def updateDescription[MT: Resource, DT: Writable](id: String, did: String, desc: DT, logMsg: Option[String] = None): Future[DT]

  /**
   * Create a new access point on the given item description.
   *
   * @param id the item's ID
   * @param did the description ID
   * @param ap the access point data
   * @param logMsg an optional log message
   * @tparam MT the item's meta type
   * @tparam AP the access point type
   */
  def createAccessPoint[MT: Resource, AP: Writable](id: String, did: String, ap: AP, logMsg: Option[String] = None): Future[AP]

  /**
   * Delete a given description.
   *
   * @param id the item's ID
   * @param did the description ID
   * @param logMsg an optional log message
   * @tparam MT the access point type
   */
  def deleteDescription[MT: Resource](id: String, did: String, logMsg: Option[String] = None): Future[Unit]

  /**
   * Delete a given access point.
   *
   * @param id the item's ID
   * @param did the description ID
   * @param apid the access point ID
   * @param logMsg an optional log message
   * @tparam MT the item's meta type
   */
  def deleteAccessPoint[MT: Resource](id: String, did: String, apid: String, logMsg: Option[String] = None): Future[Unit]
}
