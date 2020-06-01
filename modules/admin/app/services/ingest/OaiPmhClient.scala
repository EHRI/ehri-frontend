package services.ingest

import akka.stream.alpakka.xml.ParseEvent
import akka.stream.scaladsl.Source
import akka.util.ByteString
import models.admin.{OaiPmhConfig, OaiPmhIdentity}
import org.w3c.dom.Element

import scala.concurrent.Future

trait OaiPmhClient {
  def identify(endpoint: OaiPmhConfig): Future[OaiPmhIdentity]

  def listIdentifiers(endpoint: OaiPmhConfig): Source[String, _]

  def listSets(endpoint: OaiPmhConfig): Source[(String, String), _]

  def listRecords(endpoint: OaiPmhConfig): Source[Element, _]

  def getRecord(endpoint: OaiPmhConfig, id: String): Source[ByteString, _]

  def streamRecords(endpoint: OaiPmhConfig): Source[ParseEvent, _]
}
