package com.williamhill.fenix.server.es

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.typesafe.scalalogging.LazyLogging
import com.williamhill.fenix.server.config.FenixConfig
import com.williamhill.fenix.server.es.actors.EventSourceManagerActor

class EventSourceManager(config: FenixConfig, cluster: ActorRef)(implicit system: ActorSystem) extends LazyLogging {

  logger.info("Starting Fenix EventSourceManager")
  // TODO: Shall we parse the config instead ???
  val esmSingletonManager = ClusterSingletonManager.props(
    singletonProps = EventSourceManagerActor.props(config.eventSources.values.filter(_.active).toList, cluster),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)
  )
  system.actorOf(esmSingletonManager, "esm")

}

object EventSourceManager {
  def apply(config: FenixConfig, cluster: ActorRef)(implicit system: ActorSystem): EventSourceManager = new EventSourceManager(config, cluster)(system)
}
