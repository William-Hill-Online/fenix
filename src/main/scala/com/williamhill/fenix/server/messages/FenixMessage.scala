package com.williamhill.fenix.server.messages

import com.williamhill.fenix.server.util.MapUtil.MapDelta

trait FenixMessage {
  val entityId: String
}

sealed trait FenixError extends FenixMessage {
  val entityId = "error"
}

case class FenixInvalidRequestErr(err: Throwable) extends FenixError

case class FenixTopicSubscribe(topic: String) extends FenixMessage {
  override val entityId = topic
}

case class FenixTopicSubscribeAck(topic: String) extends FenixMessage {
  override val entityId = topic
}

case class FenixTopicUnsubscribe(topic: String) extends FenixMessage {
  override val entityId = topic
}

case class FenixTopicUnsubscribeAck(topic: String) extends FenixMessage {
  override val entityId = topic
}


sealed trait FenixCmd extends FenixMessage {
  val entityId: String
}

case class FenixCreateEntityCmd(entityId: String, input: Map[String, Any]) extends FenixCmd
case class FenixMergeEntityCmd(entityId: String, input: Map[String, Any], preserve: Boolean = false) extends FenixCmd
case class FenixUpdateCmd(entityId: String, field: String, value: Any) extends FenixCmd
case class FenixRemoveCmd(entityId: String, field: String) extends FenixCmd
case class FenixClearCmd(entityId: String) extends FenixCmd
case class FenixAddCmd(entityId: String, field: String, values: Seq[Any]) extends FenixCmd
case class FenixDelCmd(entityId: String, field: String, values: Seq[Any]) extends FenixCmd


sealed trait FenixEvent extends FenixMessage {
  val entityId: String
}

case class FenixCreateEntityEvent(offset: Long, entityId: String, input: Map[String, Any]) extends FenixEvent
case class FenixMergeEntityEvent(offset: Long, entityId: String, delta: MapDelta) extends FenixEvent
case class FenixUpdateEvent(offset: Long, entityId: String, field: String, value: Any) extends FenixEvent
case class FenixRemoveEvent(offset: Long, entityId: String, field: String) extends FenixEvent
case class FenixClearEvent(offset: Long, entityId: String) extends FenixEvent
case class FenixAddEvent(offset: Long, entityId: String, field: String, values: Seq[Any]) extends FenixEvent
case class FenixDelEvent(offset: Long, entityId: String, field: String, values: Seq[Any]) extends FenixEvent



sealed trait FenixQuery extends FenixMessage {
  val entityId: String
}

sealed trait FenixReq extends FenixQuery
sealed trait FenixResp extends FenixQuery
case class FenixGetEntityReq(entityId: String) extends FenixReq
case class FenixGetEntityFieldReq(entityId: String, field: String) extends FenixReq
case class FenixOutputEntityReq(entityId: String, client: (String, String)) extends FenixReq
case class FenixOutputEntityReqAndSubscribe(entityId: String, client: (String, String)) extends FenixReq
case class FenixGetEntityResp(offset: Long, entityId: String, entity: Map[String, Any]) extends FenixResp
case class FenixGetEntityFieldResp(offset: Long, entityId: String, field: String, value: Option[Any]) extends FenixResp
case class FenixOutputEntityResp(offset: Long, entityId: String, entity: Map[String, Any], client: (String, String)) extends FenixResp
case class FenixOutputEntityRespAndSubscribe(offset: Long, entityId: String, entity: Map[String, Any], client: (String, String)) extends FenixResp
