package com.williamhill.fenix.server.es.actors

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.BackoffSupervisor
import com.williamhill.fenix.server.es.EventSourceDefinition

import scala.concurrent.duration._

class EventSourceManagerActor(sources: List[EventSourceDefinition], cluster: ActorRef) extends Actor with ActorLogging {

  // TODO: Have to check the Cluster Singleton documentation, see if there's value on setting this at the preStart stage
  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    log.info("Starting EventSourceManagerActor")
    sources.foreach(
      source => {
        val supervisor = BackoffSupervisor.props(
          EventSourceActor.props(source, cluster),
          childName = source.id,
          minBackoff = 1 second,
          maxBackoff = 10 seconds,
          randomFactor = 0.2
        )
        context.actorOf(supervisor, "sup_" + source.id)
      }
    )
  }

  def receive: Receive = LoggingReceive {
    case msg => log.warning("Unsupported message: {}", msg)
  }

  // TODO: Review this strategy. Question: How does this tie to the BackoffSupervisor actor?
  override val supervisorStrategy = OneForOneStrategy(
    maxNrOfRetries = 10,
    withinTimeRange = Duration(1, TimeUnit.MINUTES)
  ) {
      case t: Throwable =>
        log.error(t, "Restarting {} due to {}", sender(), t.getMessage)
        SupervisorStrategy.Restart
    }
}

object EventSourceManagerActor {
  def props(sources: List[EventSourceDefinition], cluster: ActorRef) = Props(new EventSourceManagerActor(sources, cluster))
}