package com.williamhill.fenix.server

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe, UnsubscribeAck}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import com.williamhill.fenix.server.config.FenixConfig
import com.williamhill.fenix.server.messages._
import com.williamhill.fenix.server.util.MapUtil.MapDelta
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class FenixShardingRegionTest extends TestKit(ActorSystem(FenixShardingRegionTest.actorSystemName, FenixShardingRegionTest.config)) with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll {

  override protected def afterAll(): Unit = shutdown()

  // Manual step to Join its own cluster as there are no seeds nodes
  val cluster = Cluster(system)
  cluster.join(cluster.selfAddress)

  val fenixShard = FenixShardingRegion(new FenixConfig(FenixShardingRegionTest.config), None)
  private val mediator = DistributedPubSub(system).mediator

  def subscribeToTopic(topic: String, field: Option[String] = None): Unit = {
    val subscriptionMsg = field match {
      case Some(f) => Subscribe(topic + "/" + f, self)
      case None => Subscribe(topic, self)
    }
    mediator ! subscriptionMsg
    val _ = expectMsg(SubscribeAck(subscriptionMsg))
  }

  def unsubscribeFromTopic(topic: String, field: Option[String] = None): Unit = {
    val unsubscribeMsg = field match {
      case Some(f) => Unsubscribe(topic + "/" + f, self)
      case None => Unsubscribe(topic, self)
    }
    mediator ! unsubscribeMsg
    val _ = expectMsg(UnsubscribeAck(unsubscribeMsg))
  }

  // Test Scenarios:

  "A FenixShardRegion" should {

    "bootstrap and answer with empty data to a Fetch Request for non existing entity" in {
      val undefinedEntityId = "undefined"
      within(7 seconds) {
        fenixShard ! FenixGetEntityReq(undefinedEntityId)
        val _ = expectMsg(FenixGetEntityResp(-1, undefinedEntityId, Map()))
      }
    }

    "publish the creation of the entity" in {
      val entityId = "entity_0"
      val value = Map("foo" -> "bar", "collection" -> Map("key" -> "value"))
      subscribeToTopic(entityId)
      within(2 seconds) {
        fenixShard ! FenixCreateEntityCmd(entityId, value)
        val _ = expectMsg(FenixCreateEntityEvent(0, entityId, value))
      }
      unsubscribeFromTopic(entityId)
    }

    "answer with the actual data upon Fetch Request" in {
      val entityId = "entity_0"
      val value = Map("foo" -> "bar", "collection" -> Map("key" -> "value"))
      within(2 seconds) {
        fenixShard ! FenixGetEntityReq(entityId)
        val _ = expectMsg(FenixGetEntityResp(0, entityId, value))
      }
    }

    "answer with the actual field value upon Field Fetch Request" in {
      val entityId = "entity_0"
      within(2 seconds) {
        fenixShard ! FenixGetEntityFieldReq(entityId, "foo")
        val _ = expectMsg(FenixGetEntityFieldResp(0, entityId, "foo", Some("bar")))
      }
    }

    "publish an update of a single field of the entity" in {
      val entityId = "entity_1"
      val field = "field1"
      val value = "value_updated"
      subscribeToTopic(entityId)
      within(2 seconds) {
        fenixShard ! FenixUpdateCmd(entityId, field, value)
        val _ = expectMsg(FenixUpdateEvent(0, entityId, field, value))
      }
      unsubscribeFromTopic(entityId)
    }

    "add an element to a collection field" in {
      val entityId = "entity_2"
      val field = "collection"
      subscribeToTopic(entityId)
      within(2 seconds) {
        fenixShard ! FenixAddCmd(entityId, field, List("value1"))
        val _ = expectMsg(FenixAddEvent(0, entityId, field, List("value1")))
        fenixShard ! FenixAddCmd(entityId, field, List("value2", "value3"))
        expectMsg(FenixAddEvent(1, entityId, field, List("value2", "value3")))
        fenixShard ! FenixGetEntityReq(entityId)
        expectMsg(FenixGetEntityResp(1, entityId, Map(field -> List("value1", "value2", "value3"))))
        // TODO: Desirable to support sets
        //        fenixShard ! FenixAddCmd(entityId, field, List("value1"))
        //        expectNoMsg(500 milliseconds)
        //        fenixShard ! FenixGetEntityReq(entityId)
        //        expectMsg(FenixGetEntityResp(entityId, Map(field -> List("value1", "value2", "value3"))))
      }
      unsubscribeFromTopic(entityId)
    }

    "update a topic given a new snapshot" in {
      val entityId = "entity_3"
      subscribeToTopic(entityId)
      within(2 seconds) {
        fenixShard ! FenixMergeEntityCmd(entityId, Map("unused" -> true))
        val _ = expectMsg(FenixMergeEntityEvent(0, entityId, MapDelta(Map("unused" -> true), List())))
        fenixShard ! FenixMergeEntityCmd(entityId, Map("used" -> true))
        expectMsg(FenixMergeEntityEvent(1, entityId, MapDelta(Map("used" -> true), List("unused"))))
        // TODO: Desirable to skip notification of events that does not change the entity
        //        fenixShard ! FenixOverrideEntityCmd(entityId, Map("used" -> true))
        //        expectNoMsg(500 milliseconds)
        fenixShard ! FenixGetEntityReq(entityId)
        expectMsg(FenixGetEntityResp(1, entityId, Map("used" -> true)))
      }
      unsubscribeFromTopic(entityId)
    }

    "merge a topic given a new snapshot when preserving previous structure" in {
      val entityId = "entity_4"
      subscribeToTopic(entityId)
      within(2 seconds) {
        fenixShard ! FenixMergeEntityCmd(entityId, Map("unused" -> true), true)
        val _ = expectMsg(FenixMergeEntityEvent(0, entityId, MapDelta(Map("unused" -> true), List())))
        fenixShard ! FenixMergeEntityCmd(entityId, Map("used" -> true), true)
        expectMsg(FenixMergeEntityEvent(1, entityId, MapDelta(Map("used" -> true), List())))
        fenixShard ! FenixMergeEntityCmd(entityId, Map("new" -> "field", "used" -> false), true)
        expectMsg(FenixMergeEntityEvent(2, entityId, MapDelta(Map("new" -> "field", "used" -> false), List())))
        fenixShard ! FenixGetEntityReq(entityId)
        expectMsg(FenixGetEntityResp(2, entityId, Map("unused" -> true, "used" -> false, "new" -> "field")))
      }
      unsubscribeFromTopic(entityId)
    }

  }

}

object FenixShardingRegionTest {
  val actorSystemName = "fenix-test-as"
  val config = ConfigFactory.parseString(
    """
      |akka {
      |  loglevel = "DEBUG"
      |  actor.provider = "akka.cluster.ClusterActorRefProvider"
      |  persistence.journal.plugin = "akka.persistence.journal.inmem"
      |  cluster.metrics.enabled=off
      |  remote {
      |    log-remote-lifecycle-events = off
      |    netty.tcp {
      |      hostname = "127.0.0.1"
      |      port = 0
      |    }
      |  }
      |}
      |fenix {
      |  cluster.shardSize = 100
      |  updatesBeforeSnapshot = 100
      |  passivation.timeout = 1 minute
      |  timeout.bootstrap = 10 seconds
      |  tcp {
      |    port = 7777
      |    host = "0.0.0.0"
      |    protocol.terminal = "^"
      |    protocol.framing.charsep = true
      |  }
      |  es = []
      |}
    """.stripMargin
  )
}
