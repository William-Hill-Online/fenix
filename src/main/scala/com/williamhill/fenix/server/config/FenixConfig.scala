package com.williamhill.fenix.server.config

import com.typesafe.config.Config
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.util.Try
import com.williamhill.fenix.server.es.EventSourceDefinition
import com.williamhill.fenix.server.es.EventSource
import com.williamhill.fenix.server.messages.FenixMessage
import com.williamhill.fenix.server.channel.output.OutputChannel
import com.williamhill.fenix.server.channel.input.InputChannel

class FenixConfig(config: Config) {

  val updatesBeforeSnapshot = config.getLong("fenix.updatesBeforeSnapshot")
  val timeoutPassivation = FiniteDuration(config.getDuration("fenix.passivation.timeout").toNanos(), TimeUnit.NANOSECONDS)
  val shardSize = config.getInt("fenix.cluster.shardSize")

  val timeoutBootstrap = FiniteDuration(config.getDuration("fenix.timeout.bootstrap").toNanos(), TimeUnit.NANOSECONDS)

  val tcpPort = config.getInt("fenix.tcp.port")
  val tcpHost = config.getString("fenix.tcp.host")

  val tcpTerminalChar = config.getString("fenix.tcp.protocol.terminal").head

  /**
   * es registry
   */
  val eventSources: Map[String, EventSourceDefinition] = {
    val es = config.getObjectList("fenix.es").asScala
    es.map { obj =>
      val objConfig = obj.toConfig
      val id = objConfig.getString("id")
      val provider = Class.forName(objConfig.getString("provider")).getConstructor(classOf[Config])
      val translator = Class.forName(objConfig.getString("translator")).getConstructor()
      val active = objConfig.getBoolean("active")
      val settings = objConfig.getConfig("settings")
      (
        id,
        EventSourceDefinition(id, active, provider.newInstance(settings).asInstanceOf[EventSource],
          translator.newInstance().asInstanceOf[Any => Try[List[FenixMessage]]],
          settings)
      )
    }.toMap
  }

  val channelOutput: Option[(Class[OutputChannel], Config)] = {
    if (config.hasPath("fenix.channel.output")) {
      val channelConfig = config.getConfig("fenix.channel.output")
      val providerClass = channelConfig.getString("provider")
      val provider = Class.forName(providerClass).asInstanceOf[Class[OutputChannel]]
      Some((provider, channelConfig))
    } else None
  }

  val channelInput: Option[(InputChannel, Config)] = {
    if (config.hasPath("fenix.channel.input")) {
      val channelConfig = config.getConfig("fenix.channel.input")
      val providerClass = channelConfig.getString("provider")
      val provider = Class.forName(providerClass).getConstructor().newInstance().asInstanceOf[InputChannel]
      Some((provider, channelConfig))
    } else None
  }
}

object FenixConfig {

  def apply(config: Config): FenixConfig = new FenixConfig(config)

}