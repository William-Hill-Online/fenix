package com.williamhill.fenix.server.actors

import akka.actor.{Actor, ActorLogging, PoisonPill, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate

trait Passivation extends ActorLogging { me: Actor =>

  protected def passivate(receive: Receive): Receive = receive.orElse {
    case ReceiveTimeout =>
      log.info(s"Received Timeout passivation: ${self.path.name}")
      context.parent ! Passivate(stopMessage = PoisonPill)

    case PoisonPill =>
      log.info(s"PoisonPill: ${self.path.name}")
      context stop self
  }

}
