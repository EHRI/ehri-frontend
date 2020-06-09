package services.harvesting

import akka.stream.scaladsl.Source
import akka.util.ByteString
import models.admin.{OaiPmhConfig, OaiPmhIdentity}
import org.w3c.dom.Element

import scala.concurrent.Future

/**
  * Interact with OAI-PMH endpoints.
  *
  * Where relevant the streaming methods abstract over the
  * need to retrieve partial result sets from the underlying
  * endpoint.
  */
trait OaiPmhClient {

  /**
    * Retrieve information associated with the OAI-PMH endpoint.
    *
    * @param endpoint the endpoint config object
    * @return an OAI-PMH identity object
    */
  def identify(endpoint: OaiPmhConfig): Future[OaiPmhIdentity]

  /**
    * Retrieve object identifiers.
    *
    * @param endpoint the endpoint config object
    * @param resume   an optional resumption token
    * @return a tuple containing a set of identifier,
    *         deletion status pairs and an optional
    *         token to fetch the next set.
    */
  def listIdentifiers(endpoint: OaiPmhConfig, resume: Option[String] = None): Future[(Seq[(String, Boolean)], Option[String])]

  /**
    * Retrieve set information.
    *
    * @param endpoint the endpoint config object
    * @return a stream of tuples consisting of set identifier and name
    */
  def listSets(endpoint: OaiPmhConfig): Source[(String, String), _]

  /**
    * Retrieve a list of objects. Note: for large objects
    * this method may consume a lot of memory. Where this is
    * a concern retrieve individual items as a byte stream
    * using [[listIdentifiers()]] and [[getRecord()]]
    * respectively.
    *
    * @param endpoint the endpoint config object
    * @return a stream of XML element objects
    */
  def listRecords(endpoint: OaiPmhConfig): Source[Element, _]

  /**
    * Fetch an object via its identifier.
    *
    * @param endpoint the endpoint config object
    * @param id       the object's identifier string
    * @return a stream of bytes representing the XML document
    */
  def getRecord(endpoint: OaiPmhConfig, id: String): Source[ByteString, _]
}
