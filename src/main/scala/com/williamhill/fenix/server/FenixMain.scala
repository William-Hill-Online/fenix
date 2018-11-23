package com.williamhill.fenix.server

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import com.williamhill.fenix.server.config.FenixConfig
import com.williamhill.fenix.server.util.ConfigUtil

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object FenixMain extends App with LazyLogging {

  logger.info("===== Starting Fenix =====")
  import ConfigUtil._

  /**
   * if there is an environment variable for FENIX_SERVERS then
   * the akka.cluster.seed-nodes property should be replaced with
   * a list of string by extracting the list from the environment variable
   * and applying the trx function passed.
   */
  val appConfig = replaceLocalhost(ConfigFactory.load() ++ ("akka.cluster.seed-nodes" -> "FENIX_SERVERS", s => s"akka.tcp://fenix@$s"))

  implicit val system = ActorSystem("fenix", appConfig)
  val fenixServer = FenixServer(FenixConfig(appConfig))

  Await.ready(system.whenTerminated, Duration.Inf)
  logger.info("===== Fenix Terminated =====")

}
