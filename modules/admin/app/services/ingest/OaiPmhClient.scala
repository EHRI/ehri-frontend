package services.ingest

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.w3c.dom.Element

trait OaiPmhClient {
  def listIdentifiers(endpoint: OaiPmhConfig): Source[String, _]

  def listSets(endpoint: OaiPmhConfig): Source[(String, String), _]

  def listRecords(endpoint: OaiPmhConfig): Source[Element, _]
}
