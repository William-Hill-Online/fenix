package com.williamhill.fenix.server.util

import java.net.InetAddress

import com.typesafe.config.{Config, ConfigValueFactory}

object ConfigUtil {

  def applyStringList(config: Config, property: String, env: String, trx: String => String = s => s, separator: String = ","): Config =
    if (System.getenv().containsKey(env)) {
      val addresses = System.getenv(env).split(separator).toSeq.map { addr =>
        val parts = addr.split(":")
        val host = InetAddress.getByName(parts(0)).getHostAddress
        val port = parts(1)
        host + ":" + port
      }
      config.withValue(property, ConfigValueFactory.fromIterable(addresses.map(trx)))
    } else
      config

  implicit class ConfigExt(config: Config) {
    def ++(prop: (String, String), trx: String => String = s => s, separator: String = ","): Config =
      applyStringList(config, prop._1, prop._2, trx, separator)
  }

  private val localhost = InetAddress.getLocalHost.getHostAddress

  def replaceLocalhost(config: Config): Config = {
    val seeds = config.getStringList("akka.cluster.seed-nodes").map(s => s.replace("127.0.0.1", localhost))
    config.withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(localhost))
      .withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(seeds))
  }

  def convertToMap(config: Config): Map[String, String] =
    config.entrySet().map(entry => (entry.getKey, entry.getValue.unwrapped().toString())).toMap
}
