package com.williamhill.fenix.server.tcp

import java.nio.{ ByteBuffer, ByteOrder }
import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import akka.stream.scaladsl.Tcp._
import akka.stream.scaladsl._
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import com.williamhill.fenix.server.config.FenixConfig
import com.williamhill.fenix.server.messages.{ FenixCmd, FenixMessage, FenixReq, FenixSerializer }
import com.williamhill.fenix.server.tcp.actors.FenixTcpClientProxyActor
import com.williamhill.fenix.server.tcp.actors.FenixTcpClientProxyActor.FenixOutputChannel
import rx.lang.scala.Observable
import scala.concurrent.Future

class FenixTcpServer(config: FenixConfig, cluster: ActorRef)(implicit system: ActorSystem) extends LazyLogging {

  import config._

  logger.info("Starting Fenix TCP Server")

  implicit val materializer = ActorMaterializer()

  logger.info(s"Starting Fenix TCP Server on $tcpHost:$tcpPort")
  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp(system).bind(config.tcpHost, config.tcpPort)

  connections.runForeach { connection =>
    logger.info(s"Handling connection from: ${connection.remoteAddress}")

    val sinkActorRef = system.actorOf(FenixTcpClientProxyActor.props(connection.remoteAddress.toString(), cluster))

    val channel = framingLengthField(sinkActorRef)(connection)

    sinkActorRef ! FenixOutputChannel(channel)
  }

  def framingCharSeparator(sinkActorRef: ActorRef)(connection: IncomingConnection): ActorRef = {
    lazy val framing = Framing.delimiter(
      delimiter = ByteString(tcpTerminalChar),
      maximumFrameLength = Int.MaxValue,
      allowTruncation = true
    ).map(_.dropRight(1)).map { bs => FenixSerializer.parse(bs) }

    val serverSink = framing.to(Sink.actorRef(sinkActorRef, PoisonPill))
    val serverSource = Source.actorRef[FenixMessage](
      bufferSize = 32 * 1024,
      OverflowStrategy.dropBuffer
    ).map(fm => FenixSerializer.serialize(fm) ++ ByteString(tcpTerminalChar))

    val result = Flow.fromSinkAndSourceMat(serverSink, serverSource)(Keep.right)
    connection.flow.joinMat(result)(Keep.right).run()
  }

  def framingLengthField(sinkActorRef: ActorRef)(connection: IncomingConnection): ActorRef = {

    //    val framingLF = Framing.simpleFramingProtocol(Int.MaxValue - 4)
    //
    //    val serverSink2 = flowLF.to(Sink.actorRef(sinkActorRef, PoisonPill))
    //    val serverSource2 = Source.actorRef[FenixMessage](
    //      bufferSize = 32 * 1024,
    //      OverflowStrategy.dropBuffer
    //    ).map(fm => FenixTcpSerializer.serialize(fm)(tcpTerminalChar))
    //
    //    val result2 = Flow.fromSinkAndSourceMat(serverSink2, serverSource2)(Keep.right)
    //    val stream = framingLF.joinMat(result2)(Keep.right)
    //    connection.flow.joinMat(stream)(Keep.right).run()

    val maxPayloadSize = Int.MaxValue - 4

    // Setup the incoming Flow
    val fenixConversionFlow = Flow[ByteString].map { bs => FenixSerializer.parse(bs.drop(4)) }
    val lengthDecoderFlow = Framing.lengthField(4, 0, maxPayloadSize, ByteOrder.BIG_ENDIAN)
    val incomingFlow = lengthDecoderFlow.via(fenixConversionFlow)
    val serverSink2 = incomingFlow.to(Sink.actorRef(sinkActorRef, PoisonPill))

    // Setup the outgoing Flow
    val serverSource2 = Source.actorRef[FenixMessage](
      bufferSize = maxPayloadSize + 4,
      OverflowStrategy.dropBuffer
    ).map(fm => FenixSerializer.serialize(fm)).map(bs => ByteString(ByteBuffer.allocate(4).putInt(bs.length).array()) ++ bs)

    // Materialize the incoming + outgoing flows
    val result2 = Flow.fromSinkAndSourceMat(serverSink2, serverSource2)(Keep.right)
    connection.flow.joinMat(result2)(Keep.right).run()
  }

}

object FenixTcpServer {
  def apply(config: FenixConfig, cluster: ActorRef)(implicit system: ActorSystem) = new FenixTcpServer(config, cluster)(system)
}

trait FenixTcpClientProxy {

  def sendCmd(cmd: FenixCmd): Unit
  def sendReq(req: FenixReq): Unit
  def subscribe(topic: String): Unit
  def stream: Observable[FenixMessage]
}