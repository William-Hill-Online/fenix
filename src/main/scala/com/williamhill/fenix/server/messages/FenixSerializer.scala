package com.williamhill.fenix.server.messages

import akka.util.ByteString
import com.williamhill.fenix.server.util.JsonUtil
import com.williamhill.fenix.server.util.MapUtil.MapDelta
import play.api.libs.json._

import scala.util.Try

object FenixSerializer {

  def parse(input: ByteString): Try[FenixMessage] = Try {
    val arr = Json.parse(input.utf8String).as[Seq[JsValue]]
    val operation = arr.seq(0).as[Int]
    operation match {
      case 1 => FenixCreateEntityCmd(arr.seq(1).as[String], arr.seq(2).as[Map[String, JsValue]])
      case 2 => FenixUpdateCmd(arr.seq(1).as[String], arr.seq(2).as[String], arr.seq(3))
      case 3 => FenixRemoveCmd(arr.seq(1).as[String], arr.seq(2).as[String])
      case 4 => FenixAddCmd(arr.seq(1).as[String], arr.seq(2).as[String], arr.seq(3).as[Seq[JsValue]])
      case 5 => FenixDelCmd(arr.seq(1).as[String], arr.seq(2).as[String], arr.seq(3).as[Seq[JsValue]])
      case 6 => FenixClearCmd(arr.seq(1).as[String])
      case 7 => if (arr.size > 3) FenixMergeEntityCmd(arr.seq(1).as[String], arr.seq(2).as[Map[String, JsValue]], arr.seq(3).as[Boolean]) else FenixMergeEntityCmd(arr.seq(1).as[String], arr.seq(2).as[Map[String, JsValue]])

      case 10 => FenixGetEntityReq(arr.seq(1).as[String])
      case 11 => FenixGetEntityResp(arr.seq(1).as[Long], arr.seq(2).as[String], arr.seq(3).as[Map[String, JsValue]])
      case 12 => FenixGetEntityFieldReq(arr.seq(1).as[String], arr.seq(2).as[String])
      case 13 =>
        val opt = arr.seq(4) match {
          case JsNull => None
          case something => Some(something)
        }
        FenixGetEntityFieldResp(arr.seq(1).as[Long], arr.seq(2).as[String], arr.seq(3).as[String], opt)
      case 20 => FenixTopicSubscribe(arr.seq(1).as[String])
      case 21 => FenixTopicSubscribeAck(arr.seq(1).as[String])
      case 22 => FenixTopicUnsubscribe(arr.seq(1).as[String])
      case 23 => FenixTopicUnsubscribeAck(arr.seq(1).as[String])

      case 30 => FenixOutputEntityReq(arr.seq(1).as[String], (arr.seq(2).as[String], arr.seq(3).as[String]))
      //      case 31 => FenixOutputEntityResp(arr.seq(1).as[Long], arr.seq(2).as[String], arr.seq(3).as[Map[String, JsValue]], (arr.seq(4).as[String], arr.seq(5).as[String]))
      case 32 => FenixOutputEntityReqAndSubscribe(arr.seq(1).as[String], (arr.seq(2).as[String], arr.seq(3).as[String]))
      //      case 35 => FenixOutputEntityRespAndSubscribe(arr.seq(1).as[Long], arr.seq(2).as[String], arr.seq(3).as[Map[String, JsValue]], (arr.seq(4).as[String], arr.seq(5).as[String]))

      case 101 => FenixCreateEntityEvent(arr.seq(1).as[Long], arr.seq(2).as[String], arr.seq(3).as[Map[String, JsValue]])
      case 102 => FenixUpdateEvent(arr.seq(1).as[Long], arr.seq(2).as[String], arr.seq(3).as[String], arr.seq(4))
      case 103 => FenixRemoveEvent(arr.seq(1).as[Long], arr.seq(2).as[String], arr.seq(3).as[String])
      case 104 => FenixAddEvent(arr.seq(1).as[Long], arr.seq(2).as[String], arr.seq(3).as[String], arr.seq(4).as[Seq[JsValue]])
      case 105 => FenixDelEvent(arr.seq(1).as[Long], arr.seq(2).as[String], arr.seq(3).as[String], arr.seq(4).as[Seq[JsValue]])
      case 106 => FenixClearEvent(arr.seq(1).as[Long], arr.seq(2).as[String])
      case 107 => if (arr.size > 4)
        FenixMergeEntityEvent(arr.seq(1).as[Long], arr.seq(2).as[String], MapDelta(arr.seq(3).as[Map[String, JsValue]], arr.seq(4).as[List[String]]))
      else
        FenixMergeEntityEvent(arr.seq(1).as[Long], arr.seq(2).as[String], MapDelta(arr.seq(3).as[Map[String, JsValue]], List.empty[String]))

      case other => throw new UnsupportedOperationException(s"Operation code: $other not supported")
    }
  }

  def serializeJson(msg: FenixMessage): JsValue = msg match {
    case FenixInvalidRequestErr(err: Throwable) => Json.toJson(Seq(JsNumber(-1), JsString(err.getMessage)))
    case FenixCreateEntityCmd(entityId, data) => Json.toJson(Seq(JsNumber(1), JsString(entityId), Json.toJson(data.mapValues(JsonUtil.parseAny))))
    case FenixUpdateCmd(entityId, field, value) => Json.toJson(Seq(JsNumber(2), JsString(entityId), JsString(field), JsonUtil.parseAny(value)))
    case FenixMergeEntityCmd(entityId, data, preserve) => Json.toJson(Seq(JsNumber(7), JsString(entityId), Json.toJson(data.mapValues(JsonUtil.parseAny)), JsBoolean(preserve)))
    case FenixRemoveCmd(entityId, field) => Json.toJson(Seq(JsNumber(3), JsString(entityId), JsString(field)))
    case FenixAddCmd(entityId, field, values) => Json.toJson(Seq(JsNumber(4), JsString(entityId), JsString(field), JsonUtil.parseAny(values)))
    case FenixDelCmd(entityId, field, values) => Json.toJson(Seq(JsNumber(5), JsString(entityId), JsString(field), JsonUtil.parseAny(values)))
    case FenixClearCmd(entityId) => Json.toJson(Seq(JsNumber(6), JsString(entityId)))
    case FenixGetEntityReq(entityId) => Json.toJson(Seq(JsNumber(10), JsString(entityId)))
    case FenixGetEntityResp(offset, entityId, data) => Json.toJson(Seq(JsNumber(11), JsNumber(offset), JsString(entityId), Json.toJson(data.mapValues(JsonUtil.parseAny))))
    case FenixGetEntityFieldReq(entityId, field) => Json.toJson(Seq(JsNumber(12), JsString(entityId), JsString(field)))
    case FenixGetEntityFieldResp(offset, entityId, field, opt) =>
      val optVal = opt.map(JsonUtil.parseAny).getOrElse(JsNull)
      Json.toJson(Seq(JsNumber(13), JsNumber(offset), JsString(entityId), JsString(field), optVal))
    case FenixTopicSubscribe(topic) => Json.toJson(Seq(JsNumber(20), JsString(topic)))
    case FenixTopicSubscribeAck(topic) => Json.toJson(Seq(JsNumber(21), JsString(topic)))
    case FenixTopicUnsubscribe(topic) => Json.toJson(Seq(JsNumber(22), JsString(topic)))
    case FenixTopicUnsubscribeAck(topic) => Json.toJson(Seq(JsNumber(23), JsString(topic)))
    case FenixCreateEntityEvent(offset, entityId, data) => Json.toJson(Seq(JsNumber(101), JsNumber(offset), JsString(entityId), Json.toJson(data.mapValues(JsonUtil.parseAny))))
    case FenixMergeEntityEvent(offset, entityId, data) =>
      if (data.deletes.isEmpty)
        Json.toJson(Seq(JsNumber(107), JsNumber(offset), JsString(entityId), Json.toJson(data.updates.mapValues(JsonUtil.parseAny))))
      else
        Json.toJson(Seq(JsNumber(107), JsNumber(offset), JsString(entityId), Json.toJson(data.updates.mapValues(JsonUtil.parseAny)), JsArray(data.deletes.map(JsString(_)))))
    case FenixUpdateEvent(offset, entityId, field, value) => Json.toJson(Seq(JsNumber(102), JsNumber(offset), JsString(entityId), JsString(field), JsonUtil.parseAny(value)))
    case FenixRemoveEvent(offset, entityId, field) => Json.toJson(Seq(JsNumber(103), JsNumber(offset), JsString(entityId), JsString(field)))
    case FenixAddEvent(offset, entityId, field, values) => Json.toJson(Seq(JsNumber(104), JsNumber(offset), JsString(entityId), JsString(field), JsonUtil.parseAny(values)))
    case FenixDelEvent(offset, entityId, field, values) => Json.toJson(Seq(JsNumber(105), JsNumber(offset), JsString(entityId), JsString(field), JsonUtil.parseAny(values)))
    case FenixClearEvent(offset, entityId) => Json.toJson(Seq(JsNumber(106), JsNumber(offset), JsString(entityId)))

    case FenixOutputEntityReq(entityId, client) => Json.toJson(Seq(JsNumber(30), JsString(entityId), JsString(client._1), JsString(client._2)))
    case FenixOutputEntityResp(offset, entityId, data, client) => serializeJson(FenixGetEntityResp(offset, entityId, data))
    case FenixOutputEntityReqAndSubscribe(entityId, client) => Json.toJson(Seq(JsNumber(32), JsString(entityId), JsString(client._1), JsString(client._2)))
    case FenixOutputEntityRespAndSubscribe(offset, entityId, data, client) => serializeJson(FenixGetEntityResp(offset, entityId, data))
  }

  def serialize(msg: FenixMessage): ByteString = ByteString(Json.stringify(serializeJson(msg)))

}