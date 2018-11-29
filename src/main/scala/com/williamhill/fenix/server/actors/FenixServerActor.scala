package com.williamhill.fenix.server.actors

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings }
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.williamhill.fenix.server.FenixShardingRegion
import com.williamhill.fenix.server.channel.input.InputChannelActor
import com.williamhill.fenix.server.channel.output.OutputChannelActor
import com.williamhill.fenix.server.config.FenixConfig

import scala.concurrent.duration._

class FenixServerActor(config: FenixConfig) extends Actor with ActorLogging {

  import FenixServerActor._

  val mediator = DistributedPubSub(context.system).mediator
  implicit val timeout = Timeout(config.timeoutBootstrap)

  def running: Receive = (LoggingReceive {
    case FenixStop =>
      log.info("Terminating distributed cluster")
      context stop self
      log.info("Distributed cluster terminated")
  }: Receive).orElse(unmanaged)

  def receive: Receive = (LoggingReceive {
    case FenixStart =>
      log.info("Starting distributed cluster")
      sender ! start()
      log.info("Distributed cluster started")
      context become running
  }: Receive).orElse(unmanaged)

  private def unmanaged: Receive = {
    case msg => log.warning(s"Unmanaged message: $msg")
  }

  private def start(): ActorRef = {
    val channelInputSingletonName = "channelInputSingleton"

    val inputChannelProxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = self.path.toStringWithoutAddress + "/" + channelInputSingletonName,
        settings = ClusterSingletonProxySettings(context.system)
      ),
      name = "channelInputProxy"
    )

    val channelOutput = config.channelOutput.map { output =>
      val supervisor = BackoffSupervisor.props(
        Backoff.onStop(
          OutputChannelActor.props(output._1, output._2, inputChannelProxy),
          childName = "channelOutput",
          minBackoff = 3 seconds,
          maxBackoff = 30 seconds,
          randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
        )
      )
      context.actorOf(supervisor, "channelOutputSup")
    }

    val region: ActorRef = FenixShardingRegion(config, channelOutput)(context.system)

    config.channelInput.map { input =>
      val supervisor = BackoffSupervisor.props(
        Backoff.onStop(
          InputChannelActor.props(region, input._1, input._2), //extract config
          childName = "channelInput",
          minBackoff = 3 seconds,
          maxBackoff = 30 seconds,
          randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
        )
      )
      val singleton = ClusterSingletonManager.props(
        singletonProps = supervisor,
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(context.system)
      )
      context.actorOf(singleton, channelInputSingletonName)
    }

    region
  }

}

object FenixServerActor {
  def props(config: FenixConfig) = Props(new FenixServerActor(config))

  case object FenixStart

  case object FenixStop

}