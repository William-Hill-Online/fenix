package com.williamhill.fenix.server

import akka.actor.{ ActorRef, ActorSystem }
import akka.cluster.sharding.{ ShardRegion, ClusterSharding, ClusterShardingSettings }
import com.williamhill.fenix.server.actors.FenixEntityActor
import com.williamhill.fenix.server.config.FenixConfig
import com.williamhill.fenix.server.messages.{ FenixQuery, FenixCmd }

object FenixShardingRegion {

  def apply(config: FenixConfig, output: Option[ActorRef])(implicit system: ActorSystem): ActorRef = {
    val (extEntId, extShardId) = FenixShardingRegion.shardInfo(config.shardSize)
    ClusterSharding(system).start(
      typeName = "FenixShardRegion",
      entityProps = FenixEntityActor.props(config.updatesBeforeSnapshot, config.timeoutPassivation, output),
      settings = ClusterShardingSettings(system),
      extractEntityId = extEntId,
      extractShardId = extShardId
    )
  }

  private def shardInfo(shardSize: Long): (ShardRegion.ExtractEntityId, ShardRegion.ExtractShardId) = {
    val extractEntityId: ShardRegion.ExtractEntityId = {
      case cmd: FenixCmd => (cmd.entityId, cmd)
      case query: FenixQuery => (query.entityId, query)
    }
    val extractShardId: ShardRegion.ExtractShardId = {
      case cmd: FenixCmd => (cmd.entityId.hashCode() % shardSize).toString
      case query: FenixQuery => (query.entityId.hashCode() % shardSize).toString
    }
    (extractEntityId, extractShardId)
  }
}
