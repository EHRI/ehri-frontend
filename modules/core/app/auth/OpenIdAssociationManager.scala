package auth

import models.{OpenIDAssociation, Account}

import scala.concurrent.{Future, ExecutionContext}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait OpenIdAssociationManager {

  def findByUrl(url: String)(implicit executionContext: ExecutionContext): Future[Option[OpenIDAssociation]]

  def findAll(implicit executionContext: ExecutionContext): Future[Seq[OpenIDAssociation]]

  def addAssociation(acc: Account, assoc: String)(implicit executionContext: ExecutionContext): Future[OpenIDAssociation]
}
