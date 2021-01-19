package services.data.streams

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

object ByteStreamResizer {
  /**
    * Resize larger or smaller ByteString chunks into a constant size.
    *
    * @param chunkSize the output chunk size
    */
  def resize(chunkSize: Int): Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
    .via(Flow.fromGraph(ByteStreamResizer(chunkSize)))
}

case class ByteStreamResizer(chunkSize: Int) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in: Inlet[ByteString] = Inlet[ByteString]("ByteString.in")
  val out: Outlet[ByteString] = Outlet[ByteString]("ByteString.out")
  override val shape: FlowShape[ByteString, ByteString] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var buffer = ByteString.empty

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (isClosed(in)) emitChunks()
        else pull(in)
      }
    })
    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val elem = grab(in)
        buffer ++= elem
        emitChunks()
      }

      override def onUpstreamFinish(): Unit = {
        if (buffer.nonEmpty && isAvailable(out)) push(out, buffer)
        super.onUpstreamFinish()
      }
    })

    def emitChunks(): Unit = {
      val (sized, less) = buffer.grouped(chunkSize).partition(_.length == chunkSize)
      buffer = if (less.nonEmpty) less.next() else ByteString.empty
      if (sized.isEmpty) pull(in)
      else emitMultiple(out, sized)
    }
  }
}
