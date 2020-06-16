package services.harvesting

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.xml.ParseEvent
import akka.stream.alpakka.xml.scaladsl.{XmlParsing, XmlWriting}
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.util.ByteString
import javax.inject.Inject
import models.{OaiPmhConfig, OaiPmhIdentity}
import org.w3c.dom.Element
import play.api.i18n.Messages
import play.api.libs.ws.{WSClient, WSResponse}
import services.ingest.XmlFormatter

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

case class OaiPmhError(code: String, value: String = "") extends RuntimeException(code) {
  def errorMessage(implicit messages: Messages): String = Messages(s"oaipmh.error.$code", value)
}


case class OaiPmhClientService @Inject()(ws: WSClient)(implicit ec: ExecutionContext, mat: Materializer) extends OaiPmhClient {

  @throws[OaiPmhError]
  private def checkError(r: WSResponse): Unit = {
    if (r.status != 200) {
      throw OaiPmhError(OaiPmhConfig.URL, r.status.toString)
    }
    try {
      (r.xml \ "error").headOption.map { n =>
        throw OaiPmhError(n \@ "code", n.text)
      }
    } catch {
      case _: SAXParseException => throw OaiPmhError("invalidXml")
    }
  }

  private def stream[T](endpoint: OaiPmhConfig, params: Seq[(String, String)], transform: Flow[ParseEvent, T, NotUsed]): Source[T, _] = {

    def stage(tokenF: Future[TokenState]): Future[(Future[TokenState], Source[T, NotUsed])] = {
      tokenF.flatMap { token =>
      // NB: resumptionToken is exclusive, so if we have it we shouldn't
      // use the other control params except for the verb
      val otherParams: Seq[(String, String)] =
        if(token.asOption.isDefined) token.asOption.map(t => "resumptionToken" -> t).toSeq
        else Seq("metadataPrefix" -> endpoint.format) ++ endpoint.set.map(s => "set" -> s)

      val allParams = params ++ otherParams

        ws.url(endpoint.url)
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

  override def identify(endpoint: OaiPmhConfig): Future[OaiPmhIdentity] = {
    val verb = "Identify"
    ws.url(endpoint.url)
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
          (nodes \ "protocolVersion").text
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

  override def listIdentifiers(endpoint: OaiPmhConfig, resume: Option[String] = None): Future[(Seq[(String, Boolean)], Option[String])] = {
    val verb = "ListIdentifiers"
    val params: Seq[(String, String)] = resume.fold(
      ifEmpty = Seq("metadataPrefix" -> endpoint.format) ++ endpoint.set.map(s => "set" -> s))(
      rt => Seq("resumptionToken" -> rt))

    val allParams = Seq("verb" -> verb) ++ params

    ws.url(endpoint.url)
      .withQueryStringParameters(allParams: _*)
      .get()
      .map { r =>
        checkError(r)
        val xml = r.xml
        val idents = (xml \ verb \ "header").seq.map { node =>
          val del = (node \@ "status") == "deleted"
          val name = (node \ "identifier").text
          name -> del
        }
        val next = (xml \ verb \ "resumptionToken").headOption.map(_.text)

        idents -> next
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
    val f = ws.url(endpoint.url)
      .withQueryStringParameters(params: _*)
      .stream().map { response =>
      response.bodyAsSource
        .via(XmlParsing.parser)
        .via(XmlParsing
          .subslice(collection.immutable.Seq("OAI-PMH", "GetRecord", "record", "metadata")))
        .via(XmlFormatter.format)
        .via(XmlWriting.writer)
    }
    Source.future(f).flatMapConcat(r => r)
  }
}
