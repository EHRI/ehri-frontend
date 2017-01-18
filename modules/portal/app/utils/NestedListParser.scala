package utils

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import play.api.libs.json.{JsPath, PathNode}

class NestedListParser(path: JsPath) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in: Inlet[ByteString] = Inlet("ByteStringSource")
  val out: Outlet[ByteString] = Outlet("JsonTokenOut")

  @scala.throws[Exception](classOf[Exception])
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      var buffer: ByteString = ByteString.empty

      var token: Option[ByteString] = None
      var haveCompleteToken = false
      var inString = false

      private val nodes: List[PathNode] = path.path
      var currentNode: Option[PathNode] = None

      setHandler(in, new InHandler {

        override def onUpstreamFinish(): Unit = {
          token.foreach(t => emit(out, t))
          complete(out)
        }

        override def onPush(): Unit = {
          val byteString: ByteString = grab(in)
          pull(in)
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if (token.isDefined) {
            push(out, token.get)
            token = None
          } else {
            pull(in)
          }
        }
      })
    }

  override def shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)
}
