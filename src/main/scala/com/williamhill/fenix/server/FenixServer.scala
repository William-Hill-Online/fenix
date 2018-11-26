package com.williamhill.fenix.server

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import com.williamhill.fenix.server.actors.FenixServerActor
import com.williamhill.fenix.server.actors.FenixServerActor.FenixStart
import com.williamhill.fenix.server.config.FenixConfig
import com.williamhill.fenix.server.es.EventSourceManager
import com.williamhill.fenix.server.tcp.FenixTcpServer

import scala.concurrent.Await

class FenixServer(system: ActorSystem, config: FenixConfig) extends LazyLogging {

  private val fenixServer = system.actorOf(FenixServerActor.props(config), "fenix-server")
  private implicit val timeout: Timeout = Timeout(config.timeoutBootstrap)

  /**
   * Waiting for the system to properly bootstrap before
   * starting the TCP Server.
   */
  val cluster = Await.result((fenixServer ? FenixStart).mapTo[ActorRef], config.timeoutBootstrap)

  val tcpServer = FenixTcpServer(config, cluster)(system)

  val esManager = EventSourceManager(config, cluster)(system)
}

object FenixServer {
  def apply(config: FenixConfig)(implicit system: ActorSystem): FenixServer = new FenixServer(system, config)
}