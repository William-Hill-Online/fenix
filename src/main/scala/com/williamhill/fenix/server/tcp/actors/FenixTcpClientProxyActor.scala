package com.williamhill.fenix.server.tcp.actors

import com.williamhill.fenix.server.messages._
import com.williamhill.fenix.server.tcp.actors.FenixTcpClientProxyActor.FenixOutputChannel
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck
import akka.cluster.pubsub.DistributedPubSubMediator.Unsubscribe
import akka.cluster.pubsub.DistributedPubSubMediator.UnsubscribeAck
import akka.event.LoggingReceive
import scala.util.Success
import scala.util.Failure

class FenixTcpClientProxyActor(remoteAddress: String, cluster: ActorRef, outChannel: Option[ActorRef] = None) extends Actor with ActorLogging {

  val mediator = DistributedPubSub(context.system).mediator

  var optChannel: Option[ActorRef] = outChannel

  def receive: Receive = LoggingReceive {
    case FenixOutputChannel(output) =>
      log.info(s"Setting output channel to: $output")
      optChannel = Some(output)

    case Success(cmd: FenixCmd) =>
      log.info(s"Received command: $cmd")
      cluster ! cmd
    case Success(req: FenixReq) =>
      log.info(s"Received request: $req")
      cluster ! req
    case Success(FenixTopicSubscribe(topic)) =>
      log.info(s"Received subscription to topic: $topic")
      mediator ! Subscribe(topic, self)
    case SubscribeAck(Subscribe(topic, _, _)) => optChannel.foreach(_ ! FenixTopicSubscribeAck(topic))
    case Success(FenixTopicUnsubscribe(topic)) =>
      log.info(s"Received unsubscription to topic: $topic")
      mediator ! Unsubscribe(topic, self)
    case UnsubscribeAck(Unsubscribe(topic, _, _)) => optChannel.foreach(_ ! FenixTopicUnsubscribeAck(topic))

    case Failure(err) => optChannel.foreach(_ ! FenixInvalidRequestErr(err))

    case evt: FenixEvent => optChannel.foreach(_ ! evt)
    case resp: FenixResp => optChannel.foreach(_ ! resp)

    case akka.actor.Status.Failure(ex) => ex.printStackTrace()
    case msg =>
      log.info(s"${this.getClass.getName}-${msg.getClass().getName}<========== Message received: $msg")
  }

  override def postStop() = {
    log.info(s"Disposing client: $remoteAddress")
  }
}

object FenixTcpClientProxyActor {
  def props(remoteAddress: String, cluster: ActorRef) = Props(new FenixTcpClientProxyActor(remoteAddress, cluster))

  case class FenixOutputChannel(output: ActorRef)
}