package services.harvesting

import akka.stream.scaladsl.Source
import akka.util.ByteString
import models.{FileLink, ResourceSyncConfig}

import scala.concurrent.Future


/**
  * Minimally functional client for the OAI ResourceSync spec.
  *
  * Currently this does nothing except:
  *
  *  - parse a capabilitylist.xml for resourcelist links
  *  - read resource URLs out of those resourcelist.xml docs
  *
  * There is so far no support for changelists, resourcedumps,
  * changedumps and smart synchronisation using timestamps etc.
  */
trait ResourceSyncClient {
  /**
    * Given a config, which consists of a capabilitylist URL and
    * a regex filter, fetch a list of file URLs.
    *
    * @param config an RS config
    * @return a list of files in linked resourcelists
    */
  def list(config: ResourceSyncConfig): Future[Seq[FileLink]]

  /**
    * Fetch the byte source for a file item.
    *
    * @throws ResourceSyncError if attempting to fetch the
    *                           file's data does not return
    *                           the correct HTTP status code
    * @param link a [[FileLink]] item
    * @return the source of bytes
    */
  @throws[ResourceSyncError]
  def get(link: FileLink): Source[ByteString, _]
}
