package utils.streams

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.fasterxml.jackson.core.{JsonFactory, JsonGenerator}
import com.fasterxml.jackson.databind.ObjectMapper
import de.undercouch.actson.{JsonEvent, JsonParser}
import org.apache.commons.lang3.StringEscapeUtils
import utils.streams.JsonStream._

import scala.annotation.switch


object JsonStream {

  sealed trait JsEvent
  case object JsStartObject extends JsEvent
  case object JsStartArray extends JsEvent
  case object JsEndObject extends JsEvent
  case object JsEndArray extends JsEvent

  sealed trait JsValue extends JsEvent
  case class JsFieldName(s: String) extends JsEvent
  case class JsValueString(s: String) extends JsValue
  case class JsValueNumber(n: BigDecimal) extends JsValue
  case object JsValueTrue extends JsValue
  case object JsValueFalse extends JsValue
  case object JsValueNull extends JsValue

  /**
    * Parse a JSON byte stream into a stream of (path -> event) pairs.
    */
  def parse: Flow[ByteString, (String, JsEvent), NotUsed] = Flow[ByteString]
    .via(Flow.fromGraph(JsonStream()))

  /**
    * Accumulate JSON events at the given prefix into objects,
    * emitted as byte strings.
    *
    * @param prefix a JSON path prefix at which to emit objects. If
    *               empty will emit the entire JSON tree as a single
    *               byte string.
    */
  def accumulate(prefix: String = ""): Flow[(String, JsEvent), ByteString, NotUsed] = Flow.fromGraph(EventAccumulator(prefix))

  /**
    * Parse a JSON byte stream emitting bytes corresponding to JSON values
    * at a given path.
    *
    * @param prefix a JSON path prefix at which to emit objects. If
    *               empty will emit the entire JSON tree as a single
    *               byte string.
    */
  def items(prefix: String): Flow[ByteString, ByteString, NotUsed] = parse.via(accumulate(prefix))
}


private[streams] case class JsonStream() extends GraphStage[FlowShape[ByteString, (String, JsEvent)]] {

  val in: Inlet[ByteString] = Inlet("Bytes.in")
  val out: Outlet[(String, JsEvent)] = Outlet("JsonEvents.out")

  override def shape: FlowShape[ByteString, (String, JsEvent)] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler with StageLogging {

      // Icky mutable vars...
      private var path = ""
      private var objStart = false
      private var pos = 0
      private var buffer: Array[Byte] = Array.empty
      private var stack: List[String] = List.empty
      private val parser: JsonParser = new JsonParser(StandardCharsets.UTF_8)

      setHandlers(in, out, this)

      override def onPush(): Unit = {
        assert(pos == 0 || pos == buffer.length, s"Position outside range!: $pos, ${buffer.length}")
        buffer = grab(in).toArray
        pos = 0

        feedParser()
      }

      private def feedParserOrPull(): Unit = {
        if (pos == 0 || pos == buffer.length) {
          if (!isClosed(in)) pull(in)
          else {
            parser.getFeeder.done()
            if (parser.getFeeder.isDone) completeStage()
            else feedParser()
          }
        }
        else feedParser()
      }

      private def feedParser(): Unit = {
        pos += parser.getFeeder.feed(buffer, pos, buffer.length - pos)
        processEvents()
      }

      override def onPull(): Unit = processEvents()

      override def onUpstreamFinish(): Unit = {
        if (!parser.getFeeder.hasInput) super.onUpstreamFinish()
        else if (isAvailable(out)) feedParser()
      }


      @inline private def pushPath(s: String): Unit = {
        stack = s :: stack
        path = stack.reverse.mkString(".")
      }

      @inline private def popPath(): Unit = {
        stack = stack.tail
        path = stack.reverse.mkString(".")
      }

      @inline private def pushEvent(e: Int): Unit = push(out, path -> event(e))

      @inline private def currentString = StringEscapeUtils.unescapeJson(parser.getCurrentString)

      def event(ev: Int): JsEvent = (ev: @switch) match {
        case JsonEvent.VALUE_STRING => JsValueString(currentString)
        case JsonEvent.FIELD_NAME => JsFieldName(currentString)
        case JsonEvent.VALUE_DOUBLE => JsValueNumber(parser.getCurrentDouble)
        case JsonEvent.VALUE_INT => JsValueNumber(parser.getCurrentInt)
        case JsonEvent.VALUE_TRUE => JsValueTrue
        case JsonEvent.VALUE_FALSE => JsValueFalse
        case JsonEvent.VALUE_NULL => JsValueNull
        case JsonEvent.START_OBJECT => JsStartObject
        case JsonEvent.START_ARRAY => JsStartArray
        case JsonEvent.END_OBJECT => JsEndObject
        case JsonEvent.END_ARRAY => JsEndArray
        case _ => throw new IllegalArgumentException(s"Unexpected event: $ev")
      }

      private def processEvents(): Unit = {

        val ev = parser.nextEvent()
        if (ev != JsonEvent.EOF) {
          (ev: @switch) match {
            case JsonEvent.START_ARRAY =>
              pushEvent(ev)
              pushPath("item")
            case JsonEvent.END_ARRAY | JsonEvent.END_OBJECT =>
              popPath()
              pushEvent(ev)
            case JsonEvent.START_OBJECT =>
              objStart = true
              pushEvent(ev)
            case JsonEvent.FIELD_NAME =>
              if (!objStart) popPath()
              else objStart = false
              pushEvent(ev)
              pushPath(currentString)

            case JsonEvent.ERROR => failStage(new IllegalStateException(
                s"Malformed JSON found at position: ${parser.getParsedCharacterCount}, " +
                  s"position: $pos, current buffer: '${new String(buffer)}'"))

            case JsonEvent.NEED_MORE_INPUT => feedParserOrPull()

            case _ => pushEvent(ev)
          }
        } else {
          parser.getFeeder.done()
          completeStage()
        }
      }
    }
}

private[streams] object JsonBuilder {
  private[streams] val mapper = new ObjectMapper()
  private[streams] val factory = new JsonFactory(mapper)
}

private[streams] class JsonBuilder {
  private val buffer = new ByteArrayOutputStream()
  private val generator: JsonGenerator = JsonBuilder.factory.createGenerator(buffer)

  def value: ByteString = {
    generator.flush()
    ByteString.fromArray(buffer.toByteArray)
  }

  def event(ev: JsEvent): Unit = ev match {
    case JsStartObject => generator.writeStartObject()
    case JsStartArray => generator.writeStartArray()
    case JsEndObject => generator.writeEndObject()
    case JsEndArray => generator.writeEndArray()
    case JsFieldName(s) => generator.writeFieldName(s)
    case JsValueString(s) => generator.writeString(s)
    case JsValueNumber(n) => generator.writeNumber(n.bigDecimal)
    case JsValueTrue => generator.writeBoolean(true)
    case JsValueFalse => generator.writeBoolean(false)
    case JsValueNull => generator.writeNull()
  }

  def reset(): Unit = buffer.reset()
}

private case class EventAccumulator(prefix: String) extends GraphStage[FlowShape[(String, JsEvent), ByteString]] {
  val in: Inlet[(String, JsEvent)] = Inlet("JsonEvents.in")
  val out: Outlet[ByteString] = Outlet("JsonBytes.out")

  override def shape: FlowShape[(String, JsEvent), ByteString] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      private var endEvent: Option[JsEvent] = None
      private val builder: JsonBuilder = new JsonBuilder

      private def valueString(v: JsValue): ByteString = v match {
        case JsValueString(s) => ByteString.fromArray(JsonBuilder.mapper.writeValueAsBytes(s))
        case JsValueNumber(n) => ByteString.fromArray(JsonBuilder.mapper.writeValueAsBytes(n))
        case JsValueTrue => ByteString.fromArray(JsonBuilder.mapper.writeValueAsBytes(true))
        case JsValueFalse => ByteString.fromArray(JsonBuilder.mapper.writeValueAsBytes(false))
        case JsValueNull => ByteString.fromArray(JsonBuilder.mapper.writeValueAsBytes(null))
      }

      private def startStructure(ev: JsEvent): Unit = {
        builder.reset()
        builder.event(ev)
        endEvent = Some(if (ev == JsStartObject) JsEndObject else JsEndArray)
        setHandler(in, inObjectHandler)
        pull(in)
      }

      private def endStructure(ev: JsEvent): Unit = {
        builder.event(ev)
        endEvent = None
        setHandler(in, inHandler)
        push(out, builder.value)
      }

      val inObjectHandler: InHandler = new InHandler {
        override def onPush(): Unit = {
          val (path, ev) = grab(in)
          if (path == prefix && endEvent.contains(ev)) {
            endStructure(ev)
          } else {
            builder.event(ev)
            pull(in)
          }
        }

        override def onUpstreamFinish(): Unit = {
          push(out, builder.value)
          super.onUpstreamFinish()
        }
      }

      val inPathHandler: InHandler = new InHandler {
        override def onPush(): Unit = {
          val (path, ev) = grab(in)
          if (path != prefix) {
            setHandler(in, inHandler)
            pull(in)
          } else ev match {
            case JsStartObject | JsStartArray => startStructure(ev)
            case v: JsValue => push(out, valueString(v))
            case _ => throw new IllegalArgumentException(
              s"Unexpected non-value $ev in path handler.")
          }
        }
      }

      val inHandler: InHandler = new InHandler {
        override def onPush(): Unit = {
          val (path, ev) = grab(in)
          if (path != prefix) pull(in)
          else ev match {
            case JsStartObject | JsStartArray => startStructure(ev)
            case v: JsValue =>
              setHandler(in, inPathHandler)
              push(out, valueString(v))
            case _ => throw new IllegalArgumentException(
              s"Unexpected non-value $ev in in handler.")
          }
        }
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = pull(in)
      })

      setHandler(in, inHandler)
    }
}