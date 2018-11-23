package com.williamhill.fenix.server.channel.output

import akka.actor.{Actor, ActorLogging, ActorRef, Kill, Props, ReceiveTimeout, Stash}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import akka.event.LoggingReceive
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.williamhill.fenix.server.util.ConfigUtil
import com.williamhill.fenix.server.channel.SubscriptionsMap
import com.williamhill.fenix.server.channel.converter.WHOGatewayConverter
import com.williamhill.fenix.server.channel.messages.{ChannelSubscribe, ChannelSubscriptionMapReq, ChannelSubscriptionMapResp, ChannelUnsubscribe}
import com.williamhill.fenix.server.messages._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.collection.JavaConversions._
import scala.concurrent.duration._

trait OutputChannel {

  def send(msg: Any, client: Option[(String, String)]): Unit

  def close(): Unit

}

class KafkaOutputChannel(config: Config) extends OutputChannel with LazyLogging {

  val streamName = config.getString("stream")
  val converter = WHOGatewayConverter
  val producer = new KafkaProducer[String, String](ConfigUtil.convertToMap(config.getConfig("settings")))

  override def send(msg: Any, client: Option[(String, String)]) = msg match {
    case resp: FenixResp => deliverMessage(resp, client)
    case evt: FenixEvent => deliverMessage(evt, client)
    case any => logger.warn(s"Unknown message: $any")
  }

  private def deliverMessage(msg: FenixMessage, client: Option[(String, String)]): Unit = {
    converter.fenixRespToWHOGwResp(msg, client).foreach(converter.serialize(_).foreach(o => {
      producer.send(new ProducerRecord(streamName, o)) // TODO: handle callback of result
      logger.info(s"Message $o sent to kafka")
    }))
  }

  def close() = {
    producer.close()
  }

}

class OutputChannelActor(output: OutputChannel, inputChannelProxy: ActorRef, filterSubscriptions: Boolean) extends Actor with ActorLogging with Stash {

  val mediator = DistributedPubSub(context.system).mediator
  var subscriptionsMap = SubscriptionsMap()

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    mediator ! Subscribe("outputchannel_events", self)
    context setReceiveTimeout (10 seconds)
  }

  override def receive: Receive = LoggingReceive {
    case SubscribeAck(_) =>
      log.info("Subscribed to outputchannel_events, requesting Subscription Map to {}", inputChannelProxy)
      inputChannelProxy ! ChannelSubscriptionMapReq()
    case ChannelSubscriptionMapResp(subscriptions) =>
      log.info("Received Subscriptions Map with subscriptions for {} topics. Running ...", subscriptions.size)
      subscriptionsMap = subscriptions
      context setReceiveTimeout Duration.Undefined
      context become running
      unstashAll()
    case ReceiveTimeout =>
      log.warning("Timeout waiting for Subscriptions Map")
      self ! Kill
    case other => stash()
  }

  def running: Receive = LoggingReceive {
    case resp: FenixResp => sendResponse(resp)
    case evt: FenixEvent => sendIfSubscribed(evt)
  }.orElse(handleSubscriptions).orElse {
    case any => log.warning(s"Unknown message: $any")
  }

  private def handleSubscriptions: Receive = {
    case ChannelSubscribe(topic, client) => subscribeClient(topic, client)
    case ChannelUnsubscribe(topic, client) => unsubscribeClient(topic, client)
  }

  def sendResponse(resp: FenixResp) = {
    val target = resp match {
      case FenixOutputEntityRespAndSubscribe(_, topic, _, client) =>
        subscribeClient(topic, client)
        Some(client)
      case FenixOutputEntityResp(_, _, _, client) =>
        Some(client)
      case other => None
    }
    output.send(resp, target)
  }

  private def sendIfSubscribed(msg: FenixMessage) = if (filterSubscriptions) {
    val topic = msg.entityId
    // TODO: We'll broadcast for now...
    if (subscriptionsMap.getSubscriberGroups(topic).nonEmpty)
      output.send(msg, None)
    // instead of sending to each client: subscriptionsMap.getSubscribers(topic).foreach(c => output.send(msg, Some(c)))
    // or broadcast to the group: subscriptionsMap.getSubscriberGroups(topic).foreach(g => output.send(msg, Some((g, ""))))
  } else {
    output.send(msg, None)
  }

  def subscribeClient(topic: String, client: (String, String)) = {
    // NOTE: Subscriptions are managed in a per-router basis
    val clientForSubscription = (client._1, "")
    if (!subscriptionsMap.isSubscribed(topic, clientForSubscription)) {
      log.info("Subscribing client {} to topic {}", clientForSubscription, topic)
      subscriptionsMap = subscriptionsMap.withSubscription(topic, clientForSubscription)
    } else {
      log.info("Ignoring subscription request from client {} to topic {} as is already subscribed to it", clientForSubscription, topic)
    }
  }

  def unsubscribeClient(topic: String, client: (String, String)) = {
    // NOTE: Subscriptions are managed in a per-router basis
    val clientForUnsubscription = (client._1, "")
    if (subscriptionsMap.isSubscribed(topic, clientForUnsubscription)) {
      log.info("Unsubscribing client {} from topic {}", clientForUnsubscription, topic)
      subscriptionsMap = subscriptionsMap.withoutSubscription(topic, clientForUnsubscription)
    } else {
      log.info("Ignoring unsubscription request from client {} to topic {} as is not subscribed to it", clientForUnsubscription, topic)
    }
  }

  override def postStop() = {
    output.close()
  }

}

object OutputChannelActor {
  // Needs to instantiate a new OutputChannel every time the actor is instantiated, otherwise,
  // if channel is closed and actor restarted, it'll try to reuse the same closed channel
  def props(out: Class[OutputChannel], config: Config, inputChannelProxy: ActorRef) =
    Props(new OutputChannelActor(out.getConstructor(classOf[Config]).newInstance(config), inputChannelProxy, config.getBoolean("filterBySubscription")))
}