package auth

import models.OpenIDAssociation
import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait OpenIdAssociationManager {

  def findByUrl(url: String): Future[Option[OpenIDAssociation]]

  def findAll: Future[Seq[OpenIDAssociation]]

  def addAssociation(id: String, assoc: String): Future[Option[OpenIDAssociation]]
}
