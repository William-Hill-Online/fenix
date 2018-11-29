package com.williamhill.fenix.server.channel.input

import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.event.LoggingReceive
import akka.persistence._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.williamhill.fenix.server.util.KafkaUtil
import com.williamhill.fenix.server.channel.SubscriptionsMap
import com.williamhill.fenix.server.channel.converter._
import com.williamhill.fenix.server.channel.messages.{ ChannelSubscribe, ChannelSubscriptionMapReq, ChannelSubscriptionMapResp, ChannelUnsubscribe }
import com.williamhill.fenix.server.messages._
import rx.lang.scala.Observable

trait InputChannel extends (Config => Observable[WHOGatewayMessage])

class KafkaInputChannel extends InputChannel with LazyLogging {

  val converter = WHOGatewayConverter

  override def apply(config: Config): Observable[WHOGatewayMessage] = {
    logger.info(s"Starting input channel on topic: ${config.getString("stream")}")

    KafkaUtil.createSource(Seq(config.getString("stream")), config.getConfig("settings"), 100)
      .map { in =>
        val input = converter.parse(in)
        logger.info(s"Received message from inputchannel: $in -> $input")
        input
      }
      .filter(_.isSuccess)
      .map(_.get)
  }
}

class InputChannelActor(cluster: ActorRef, input: InputChannel, config: Config) extends PersistentActor with ActorLogging {

  var subscriptionsMap = SubscriptionsMap()
  val mediator = DistributedPubSub(context.system).mediator
  val subscription = input(config).subscribe(
    next => self ! next,
    err => throw err
  )

  val snapshotThreshold = config.getLong("snapshot.threshold")
  var snapshotCounter: Long = 0

  override def persistenceId: String = "fenix_input_channel"

  override def receiveRecover: Receive = (LoggingReceive {
    case RecoveryCompleted => log.info("Input channel fully recovered with subscriptions for {} topics", subscriptionsMap.size)
    case SnapshotOffer(metadata, InputChannelSnapshot(subscriptions)) =>
      log.info("recovering snapshot {}", metadata, subscriptions.size)
      subscriptionsMap = subscriptions
    case subsEvent: AddSubscriptionEvent =>
      log.debug(s"recovering with subscription request: $subsEvent")
      incrementSnapshotCounter()
      subscriptionsMap = subscriptionsMap.withSubscription(subsEvent.topic, subsEvent.client)
    case unsubsEvent: RemoveSubscriptionEvent =>
      log.debug(s"recovering with unsubscription request: $unsubsEvent")
      incrementSnapshotCounter()
      subscriptionsMap = subscriptionsMap.withoutSubscription(unsubsEvent.topic, unsubsEvent.client)
  }: Receive).orElse(unknown)

  override def receiveCommand: Receive = (LoggingReceive {
    case msg @ WHOGwFetchReq(topic, routerId, clientId) =>
      cluster ! FenixOutputEntityReq(topic, (routerId, clientId))
    case msg @ ChannelSubscriptionMapReq() =>
      log.info("Subscriptions Map requested by {}", sender)
      sender ! ChannelSubscriptionMapResp(subscriptionsMap)
  }: Receive).orElse(handleWHOGwSubscriptionsCmds).orElse(handleSnapshotSaving).orElse(unknown)

  def handleWHOGwSubscriptionsCmds: Receive = LoggingReceive {
    // Async persistence as: persisting subscriptions is not critical and execution order of the callbacks are guaranteed
    case msg @ WHOGwSubscribeReq(topic, routerId, clientId) =>
      log.info("Received subscription request from {}/{} to topic {}", routerId, clientId, topic)
      addSubscriptionAndFetch(topic, (routerId, clientId)).foreach(
        event => {
          val counter = incrementSnapshotCounter()
          val actualSubscriptions = subscriptionsMap
          persistAsync(event) { _ => if (counter == -1) saveSnapshot(InputChannelSnapshot(actualSubscriptions)) }
        }
      )
    case msg @ WHOGwUnsubscribeReq(topic, routerId, clientId) =>
      log.info("Client {}/{} request unsuscribe from topic {}", routerId, clientId, topic)
      removeSubscription(topic, (routerId, clientId)).foreach(
        event => {
          val counter = incrementSnapshotCounter()
          val actualSubscriptions = subscriptionsMap
          persistAsync(msg) { _ => if (counter == -1) saveSnapshot(InputChannelSnapshot(actualSubscriptions)) }
        }
      )
  }

  private def handleSnapshotSaving: Receive = {
    case SaveSnapshotSuccess(metadata) => log.info("Snapshot saved: {}", metadata)
    case SaveSnapshotFailure(metadata, cause) => log.error("Failed to save snapshot: {}", cause, metadata)
  }

  def unknown: Receive = LoggingReceive {
    case msg => log.warning(s"Unexpected message: $msg")
  }

  override def postStop() = {
    subscription.unsubscribe()
  }

  private def addSubscriptionAndFetch(topic: String, client: (String, String)): Option[InputChannelEvent] = {
    // The fetch takes places even if the client was already subscribed
    cluster ! FenixOutputEntityReqAndSubscribe(topic, client)

    // NOTE: Subscriptions are managed in a per-router basis
    if (!subscriptionsMap.isSubscribed(topic, clientForSubscription(client))) {
      subscriptionsMap = subscriptionsMap.withSubscription(topic, clientForSubscription(client))
      mediator ! Publish("outputchannel_events", ChannelSubscribe(topic, clientForSubscription(client)))
      Some(AddSubscriptionEvent(topic, clientForSubscription(client)))
    } else {
      None
    }
  }

  private def removeSubscription(topic: String, client: (String, String)): Option[InputChannelEvent] = {
    // NOTE: Subscriptions are managed in a per-router basis
    if (subscriptionsMap.isSubscribed(topic, clientForSubscription(client))) {
      subscriptionsMap = subscriptionsMap.withoutSubscription(topic, clientForSubscription(client))
      mediator ! Publish("outputchannel_events", ChannelUnsubscribe(topic, clientForSubscription(client)))
      Some(RemoveSubscriptionEvent(topic, clientForSubscription(client)))
    } else {
      None
    }
  }

  private def clientForSubscription(client: (String, String)): (String, String) = (client._1, "")

  private def incrementSnapshotCounter(): Long = {
    if (snapshotCounter == snapshotThreshold)
      snapshotCounter = -1
    else
      snapshotCounter += 1
    snapshotCounter
  }
}

case class InputChannelSnapshot(subscriptionsMap: SubscriptionsMap)

sealed trait InputChannelEvent {
  val topic: String
  val client: (String, String)
}

case class AddSubscriptionEvent(topic: String, client: (String, String)) extends InputChannelEvent

case class RemoveSubscriptionEvent(topic: String, client: (String, String)) extends InputChannelEvent

object InputChannelActor {
  def props(cluster: ActorRef, input: InputChannel, config: Config) = Props(new InputChannelActor(cluster, input, config))
}
