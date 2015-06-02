package backend

import scala.concurrent.Future
import utils._


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Events {
  /**
   * Fetch the global event stream.
   *
   * @param params range params
   * @param filters event filter params
   */
  def listEvents[A: Readable](params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]]

  /**
   * Fetch a personalised event stream for a given user.
   *
   * @param userId the user's id
   * @param params range params
   * @param filters event filter params
   */
  def listEventsForUser[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]]

  /**
   * Fetch a list of events corresponding to a user's actions.
   *
   * @param userId the user's id
   * @param params range params
   * @param filters event filter params
   */
  def listUserActions[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]]

  /**
   * Fetch an item's history.
   *
   * @param id the item id
   * @param params range params
   * @param filters event filter params
   */
  def history[A: Readable](id: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]]

  /**
   * Fetch versions for an item.
   *
   * @param id the item id
   * @param params range params
   */
  def versions[V: Readable](id: String, params: PageParams): Future[Page[V]]

  /**
   * Fetch all subjects for a given event.
   *
   * @param id the item id
   * @param params range params
   */
  def subjectsForEvent[A: Readable](id: String, params: PageParams): Future[Page[A]]
}
