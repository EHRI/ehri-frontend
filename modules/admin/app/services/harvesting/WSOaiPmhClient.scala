package services.harvesting

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.xml.ParseEvent
import akka.stream.alpakka.xml.scaladsl.{XmlParsing, XmlWriting}
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.util.ByteString
import models.OaiPmhIdentity.Granularity
import models.{OaiPmhConfig, OaiPmhIdentity}
import org.w3c.dom.Element
import play.api.Logger
import play.api.http.HeaderNames
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import services.ingest.XmlFormatter

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.SAXParseException

sealed trait TokenState {
  def asOption: Option[String] = this match {
    case Resume(token) => Some(token)
    case _ => None
  }
}

case object Initial extends TokenState

case class Resume(token: String) extends TokenState

case object Final extends TokenState


case class WSOaiPmhClient @Inject()(ws: WSClient)(implicit ec: ExecutionContext, mat: Materializer) extends OaiPmhClient {

  private val logger = Logger(classOf[WSOaiPmhClient])

  @throws[OaiPmhError]
  private def checkError(r: WSResponse): Unit = try {
    if (r.status != 200) throw OaiPmhError(OaiPmhConfig.URL, r.status.toString)
    else (r.xml \ "error").headOption.map(n => throw OaiPmhError(n \@ "code", n.text))
  } catch {
    case _: SAXParseException => throw OaiPmhError("invalidXml")
  }

  private def stream[T](endpoint: OaiPmhConfig, params: Seq[(String, String)], transform: Flow[ParseEvent, T, NotUsed]): Source[T, _] = {

    def stage(tokenF: Future[TokenState]): Future[(Future[TokenState], Source[T, NotUsed])] = {
      tokenF.flatMap { token =>
        // NB: resumptionToken is exclusive, so if we have it we shouldn't
        // use the other control params except for the verb
        val otherParams: Seq[(String, String)] =
          if (token.asOption.isDefined) token.asOption.map(t => "resumptionToken" -> t).toSeq
          else Seq("metadataPrefix" -> endpoint.format) ++ endpoint.set.map(s => "set" -> s)

        val allParams = params ++ otherParams

        newRequest(endpoint)
          .withQueryStringParameters(allParams: _*)
          .stream().map { response =>

          response.bodyAsSource
            .via(XmlParsing.parser)
            .viaMat(OaiPmhTokenParser.parser)(Keep.right)
            .viaMat(transform)(Keep.left)
            .preMaterialize()
        }
      }
    }

    Source.unfoldAsync[Future[TokenState], Source[T, _]](Future.successful(Initial)) { ft =>
      ft.flatMap {
        case Final => Future.successful(None)
        case _ => stage(ft).map(next => Some(next))
      }
    }.flatMapConcat(f => f)
  }

  private def newRequest(endpoint: OaiPmhConfig): WSRequest = ws
    .url(endpoint.url)
    .withHttpHeaders(endpoint.auth.toSeq.map(s => HeaderNames.AUTHORIZATION -> s"Basic ${s.encodeBase64}"): _*)

  private def getFormattedTime(endpoint: OaiPmhConfig, time: Option[Instant]): Future[Option[String]] = {
    time.fold(Future.successful(Option.empty[String])) { fromTime =>
      val fmtF = identify(endpoint).map(_.granularity).map {
        case Granularity.Second => DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'")
        case _ => DateTimeFormatter.ISO_DATE
      }
      fmtF.map(fmt => Some(fmt.format(fromTime.atOffset(ZoneOffset.UTC))))
    }
  }

  // In cases where we have `from` or `to` parameters the only way to properly
  // format the date - with either day or second granularity - depends on the
  // endpoint itself and the result of the `identify` verb. So if we have a
  // `from` parameter and we're not resuming we have to run `identify` prior
  // to the List* commands
  private def getListParams(verb: String,
    endpoint: OaiPmhConfig,
    from: Option[Instant],
    resume: Option[String]): Future[Seq[(String, String)]] = {
    resume.fold(
      ifEmpty = getFormattedTime(endpoint, from).map { timeOpt =>
        logger.debug(s"Harvesting with `from` time: $timeOpt")
        Seq(
          "verb" -> verb,
          "metadataPrefix" -> endpoint.format
        ) ++ endpoint.set.map("set" -> _) ++ timeOpt.map("from" -> _)
      }
    )(token => Future.successful(Seq("verb" -> verb, "resumptionToken" -> token)))
  }

  override def identify(endpoint: OaiPmhConfig): Future[OaiPmhIdentity] = {
    val verb = "Identify"
    newRequest(endpoint)
      .withQueryStringParameters("verb" -> verb)
      .get()
      .map { r =>
        checkError(r)
        r.xml \ verb
      }
      .map { nodes =>
        OaiPmhIdentity(
          (nodes \ "repositoryName").text,
          (nodes \ "baseURL").text,
          (nodes \ "protocolVersion").text,
          if ((nodes \ "granularity").text.trim == Granularity.Second.toString)
            Granularity.Second
          else Granularity.Day
        )
      }
  }

  override def listSets(endpoint: OaiPmhConfig): Source[(String, String), _] = {
    val t: Flow[ParseEvent, (String, String), NotUsed] = XmlParsing
      .subtree(collection.immutable.Seq("OAI-PMH", "ListSets", "set"))
      .map { elem =>
        val spec = elem.getElementsByTagName("setSpec").item(0)
        val name = elem.getElementsByTagName("setName").item(0)
        Option(spec) -> Option(name)
      }.collect {
      case (Some(specElem), Some(nameElem)) =>
        specElem.getTextContent -> nameElem.getTextContent
    }
    stream(endpoint, Seq("verb" -> "ListSets"), t)
  }

  override def listIdentifiers(endpoint: OaiPmhConfig, from: Option[Instant], resume: Option[String] = None): Future[(Seq[(String, Boolean)], Option[String])] = {
    val verb = "ListIdentifiers"
    val paramsF = getListParams(verb, endpoint, from, resume)

    paramsF.flatMap { params =>
      newRequest(endpoint)
        .withQueryStringParameters(params: _*)
        .get()
        .map { r =>
          checkError(r)
          val xml = r.xml
          val idents = (xml \ verb \ "header").seq.map { node =>
            val del = (node \@ "status") == "deleted"
            val name = (node \ "identifier").text
            name -> del
          }
          val next = (xml \ verb \ "resumptionToken").headOption.map(_.text).filter(_.trim.nonEmpty)

          idents -> next
        }
    }
  }

  override def listRecords(endpoint: OaiPmhConfig): Source[Element, _] = {
    // FIXME: this is really not nice.
    val t: Flow[ParseEvent, Element, NotUsed] = XmlParsing
      .subtree(collection.immutable.Seq("OAI-PMH", "ListRecords", "record", "metadata"))
      .map { elem =>
        val nodes = elem.getChildNodes
        0.to(nodes.getLength)
          .map(i => nodes.item(i))
          .find(_.getNodeType == org.w3c.dom.Node.ELEMENT_NODE)
      }
      .collect { case Some(elem: Element) => elem }
    stream(endpoint, Seq("verb" -> "ListRecords"), t)
  }

  override def getRecord(endpoint: OaiPmhConfig, id: String): Source[ByteString, _] = {
    val params = Seq(
      "verb" -> "GetRecord",
      "identifier" -> id,
      "metadataPrefix" -> endpoint.format
    )
    val f = newRequest(endpoint)
      .withQueryStringParameters(params: _*)
      .stream()
      .map { _.bodyAsSource
          .via(XmlParsing.parser)
          .via(XmlParsing
            .subslice(collection.immutable.Seq("OAI-PMH", "GetRecord", "record", "metadata")))
          .via(XmlFormatter.format)
          .via(XmlWriting.writer)
    }
    Source.future(f).flatMapConcat(r => r)
  }
}
