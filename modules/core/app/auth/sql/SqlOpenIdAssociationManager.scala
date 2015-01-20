package auth.sql

import auth.OpenIdAssociationManager
import models.{OpenIDAssociation, Account}

import scala.concurrent.{Future, ExecutionContext}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SqlOpenIdAssociationManager() extends OpenIdAssociationManager{
  def findByUrl(url: String)(implicit executionContext: ExecutionContext): Future[Option[OpenIDAssociation]] = ???

  def findForAccount(account: Account)(implicit executionContext: ExecutionContext): Future[Seq[OpenIdAssociationManager]] = ???

  def addAssociation(acc: Account, assoc: String)(implicit executionContext: ExecutionContext): Future[OpenIDAssociation] = ???

  def findAll(implicit executionContext: ExecutionContext): Future[Seq[OpenIDAssociation]] = ???
}
