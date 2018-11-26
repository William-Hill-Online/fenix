package com.williamhill.fenix.server.es.actors

import akka.actor.{ Actor, ActorLogging, ActorRef, Kill, Props }
import com.williamhill.fenix.server.es.EventSourceDefinition
import com.williamhill.fenix.server.es.EventSourceDefinition

class EventSourceActor(definition: EventSourceDefinition, destination: ActorRef) extends Actor with ActorLogging {

  val subscription = definition.provider.createSource().subscribe(
    onNext = item => {
    log.debug("Received: {}", item)
    definition.translator(item).map(_.foreach(destination ! _)).recover {
      case err: Throwable =>
        log.error(err, "Unable to translate item: ", item.toString)
    }
    ()
  },
    onError = throwable => {
    log.error(throwable, throwable.getMessage)
    self ! Kill
  },
    onCompleted = () => {
    context stop self
  }
  )
  log.info("Started EventSourceActor {}", self.path.name)

  override def receive: Receive = {
    case msg => log.warning("Unsupported message: {}", msg)
  }

  override def postStop = {
    subscription.unsubscribe
  }

}

object EventSourceActor {
  def props(definition: EventSourceDefinition, destination: ActorRef) = Props(new EventSourceActor(definition, destination))
}
