package services.harvesting

import java.util.regex.Pattern
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString

import javax.inject.Inject
import models._
import org.xml.sax.SAXParseException
import play.api.libs.ws.{WSAuthScheme, WSClient, WSRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}
import scala.xml.{Node, NodeSeq}

/**
  * TODO: needs a lot of work this one
  *
  *  - handle changelists
  *  - handle changedumps
  *  - prevent infinite redirection
  *
  */
case class WSResourceSyncClient @Inject()(ws: WSClient)(implicit mat: Materializer) extends ResourceSyncClient {

  private implicit val ec: ExecutionContext = mat.executionContext

  private def parseAttr(nodes: NodeSeq, key: String): Option[String] =
    nodes.headOption.flatMap(n => parseAttr(n, key))

  private def parseAttr(node: Node, key: String): Option[String] =
    node.attribute(key).flatMap(_.headOption.map(_.text))

  private def wsReq(config: ResourceSyncConfig, url: String): WSRequest = config.auth.fold(ws.url(url)) { case BasicAuthConfig(username, password) =>
    ws.url(url).withAuth(username, password, WSAuthScheme.BASIC).withFollowRedirects(true)
  }

  private def readUrlSet(config: ResourceSyncConfig, filter: String => Boolean): Future[Seq[ResourceSyncItem]] = {
    wsReq(config, config.url).get().map { r =>
      try {
        (r.xml \ "url").flatMap { urlNode =>
          val md = urlNode \ "md"
          val lastMod = urlNode \ "lastmod"
          val capability = parseAttr(md, "capability")

          (urlNode \ "loc").flatMap { locNode =>
            val loc = locNode.text
            capability match {
              case Some("resourcelist") => Some(ResourceList(loc))
              case Some("capabilitylist") => Some(CapabilityList(loc))
              case None if filter(loc) => Some(FileLink(
                loc,
                contentType = parseAttr(md, "type"),
                hash = parseAttr(md, "hash"),
                length = parseAttr(md, "length").map(_.toLong),
                updated = lastMod.headOption.map(n => java.time.Instant.parse(n.text))
              ))
              case _ => None
            }
          }
        }
      } catch {
        case _: SAXParseException =>
          throw ResourceSyncError("badFormat", config.url)
      }
    }
  }

  override def list(config: ResourceSyncConfig): Future[Seq[FileLink]] = {

    val test: String => Boolean = config.filter.fold(ifEmpty = (_: String) => true)(
      s => t => Pattern.compile(s).matcher(t).find())

    readUrlSet(config, test).flatMap { list =>

      val s: Source[FileLink, NotUsed] = Source(list.toList)
        .mapAsync(1) {
          case item: FileLink => immediate(Seq(item))
          case ResourceList(loc) => readUrlSet(config.copy(url = loc), test)
          // TODO: other states...
          case _ => immediate(Seq.empty)
        }
        .flatMapConcat(s => Source(s.toList))
        .collect { case link: FileLink => link }

      s.runWith(Sink.seq)
    }
  }

  override def get(config: ResourceSyncConfig, link: FileLink): Source[ByteString, _] =
    Source.future(wsReq(config, link.loc).get().map { r =>
      if (r.status == 404) {
        Source.failed(ResourceSyncError("notFound", link.loc))
      } else if (r.status != 200) {
        Source.failed(ResourceSyncError("unexpectedStatus", r.status.toString))
      } else r.bodyAsSource
    }).flatMapConcat(src => src)
}
