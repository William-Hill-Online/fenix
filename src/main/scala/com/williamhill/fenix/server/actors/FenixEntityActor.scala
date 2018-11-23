package com.williamhill.fenix.server.actors

import java.net.URLDecoder

import akka.actor.{ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator._
import akka.event.LoggingReceive
import akka.persistence._
import com.williamhill.fenix.server.messages._
import com.williamhill.fenix.server.util.MapUtil

import scala.concurrent.duration.Duration
import scala.util.Try

class FenixEntityActor(updatesBeforeSnapshot: Long, passivation: Duration, output: Option[ActorRef], initialData: Map[String, Any]) extends PersistentActor with Passivation {


  val mediator = DistributedPubSub(context.system).mediator

  val entityId = URLDecoder.decode(self.path.name, "utf8")
  override val persistenceId = entityId

  /**
   * Mutable data: [State]
   */
  var data: Map[String, Any] = initialData
  var updates: Long = 0L
  var offset: Long = 0L

  context.setReceiveTimeout(passivation)

  override def receiveCommand: Receive = LoggingReceive(passivate {
    case cmd: FenixCmd =>
      persist(processCmd(cmd)) { evt =>
        handleEvent(evt)
        publishChange(evt)
        sendMessageToOutput(evt)
        saveSnapshotIfNecessary().recover {
          case ex: Throwable => log.error(ex, s"Error while creating snapshot for entity: $entityId")
        }
        incrementOffset()
      }
    case req: FenixReq =>
      handleRequest(req, sender)
  }).orElse(handleSnapshotSaving)
    .orElse(unknown)

  override def receiveRecover: Receive = LoggingReceive {
    case RecoveryCompleted => log.info(s"entity: $entityId fully recovered")
    case SnapshotOffer(_, FenixEntitySnapshot(offset, snapshot)) =>
      log.info(s"recovering snapshot: $snapshot"); this.data = snapshot; this.offset = offset
    case event: FenixEvent =>
      log.info(s"recovering with event: $event")
      handleEvent(event)
      incrementOffset()
  }.orElse(unknown)

  private def processCmd: FenixCmd => FenixEvent = {
    case FenixCreateEntityCmd(_, input) => FenixCreateEntityEvent(offset, entityId, input)
    case FenixMergeEntityCmd(_, input, preserve) => FenixMergeEntityEvent(offset, entityId, MapUtil.calculateDelta(data, input, preserve))
    case FenixUpdateCmd(_, field, value) => FenixUpdateEvent(offset, entityId, field, value)
    case FenixRemoveCmd(_, field) => FenixRemoveEvent(offset, entityId, field)
    case FenixAddCmd(_, field, values) => FenixAddEvent(offset, entityId, field, values)
    case FenixDelCmd(_, field, values) => FenixDelEvent(offset, entityId, field, values)
    case FenixClearCmd(_) => FenixClearEvent(offset, entityId)
  }

  private def handleEvent: Receive = {
    case FenixCreateEntityEvent(_, _, input) =>
      data = input
    case FenixMergeEntityEvent(_, _, delta) =>
      delta.updates.foreach(e => data += (e._1 -> e._2))
      delta.deletes.foreach(e => data -= e)
    case FenixUpdateEvent(_, _, field, value) =>
      data += (field -> value)
    case FenixRemoveEvent(_, _, field) =>
      data -= field
    case FenixClearEvent(_, _) =>
      data = Map.empty[String, Any]
    case FenixAddEvent(_, _, field, values) =>
      data.getOrElse(field, Seq.empty[Any]) match {
        case array: Seq[Any] =>
          data += (field -> (array ++ values))
        case value => data += (field -> (Seq(value) ++ values))
      }
    case FenixDelEvent(_, _, field, values) =>
      data.getOrElse(field, Seq[String]()) match {
        case array: Seq[Any] => data += (field -> (array diff values))
        case value: Any => data += (field -> (Seq(value) diff values))
      }
  }

  private def handleRequest(request: FenixReq, sender: ActorRef) = request match {
    case FenixGetEntityReq(_) => sender ! FenixGetEntityResp(offset - 1, entityId, data)
    case FenixGetEntityFieldReq(_, field) => sender ! FenixGetEntityFieldResp(offset - 1, entityId, field, data.get(field))
    case FenixOutputEntityReq(_, client) => sendMessageToOutput(FenixOutputEntityResp(offset - 1, entityId, data, client))
    case FenixOutputEntityReqAndSubscribe(_, client) => sendMessageToOutput(FenixOutputEntityRespAndSubscribe(offset - 1, entityId, data, client))
  }

  private def incrementOffset(): Unit = {
    offset = (offset + 1) % Long.MaxValue
  }

  private def saveSnapshotIfNecessary(n: Long = 1L): Try[Unit] = Try {
    updates = (updates + n)
    if (updates >= updatesBeforeSnapshot) {
      saveSnapshot(FenixEntitySnapshot(offset, data))
      updates = 0
    }
  }

  private def publishChange(evt: FenixEvent): Unit = {
    mediator ! Publish(entityId, evt)
    evt match {
      case FenixUpdateEvent(_, entityId, field, _) => mediator ! Publish(s"$entityId/$field", evt)
      case FenixRemoveEvent(_, entityId, field) => mediator ! Publish(s"$entityId/$field", evt)
      case FenixAddEvent(_, entityId, field, _) => mediator ! Publish(s"$entityId/$field", evt)
      case FenixDelEvent(_, entityId, field, _) => mediator ! Publish(s"$entityId/$field", evt)
      case FenixCreateEntityEvent(_, _, _) => // nothing to do yet
      case FenixMergeEntityEvent(offset, entityId, delta) => // TODO: Nothing to do yet as we only handle subscriptions to the whole topic
      //        delta.updates.foreach(u => mediator ! Publish(s"$entityId/${u._1}", FenixUpdateEvent(offset, entityId, u._1, u._2)))
      //        delta.deletes.foreach(d => mediator ! Publish(s"$entityId/$d", FenixRemoveEvent(offset, entityId, d)))
      case FenixClearEvent(_, _) => //TODO: should notify all the individual-key's subscribers
      case _ => log.warning(s"Unknown event: $evt")
    }
  }

  private def sendMessageToOutput(msg: FenixMessage): Unit = {
    output.foreach(_ ! msg)
  }

  private def handleSnapshotSaving: Receive = {
    case SaveSnapshotSuccess(_) => log.info(s"Snapshot saved for entity: $entityId")
    case SaveSnapshotFailure(_, cause) => log.error("Failed to save snapshot", cause)
  }

  private def unknown: Receive = {
    case msg => log.warning(s"Unhandled message: $msg for entity: $entityId")
  }

}

case class FenixEntitySnapshot(offset: Long, data: Map[String, Any])

object FenixEntityActor {
  def props(updatesBeforeSnapshot: Long, passivation: Duration, output: Option[ActorRef] = None, initialData: Map[String, Any] = Map.empty[String, Any]): Props =
    Props(new FenixEntityActor(updatesBeforeSnapshot, passivation, output, initialData))

}