package com.williamhill.fenix.server.channel.converter

import com.williamhill.fenix.server.util.JsonUtil
import com.williamhill.fenix.server.messages._
import play.api.libs.json._

import scala.util.Try

object WHOGatewayConverter {

  import WHOGatewayMessageConstants._

  def parse(in: String): Try[WHOGatewayMessage] = Try {
    val jsonMap = Json.parse(in).as[Map[String, JsValue]]
    jsonMap.get("C").get.as[Int] match {
      case 2 => WHOGwFetchReq(jsonMap.get(KEY).get.as[String], jsonMap.get(ROUTER).get.as[String], jsonMap.get(CLIENT).get.as[String])
      case 3 => WHOGwSubscribeReq(jsonMap.get(KEY).get.as[String], jsonMap.get(ROUTER).get.as[String], jsonMap.get(CLIENT).get.as[String])
      case 4 => WHOGwUnsubscribeReq(jsonMap.get(KEY).get.as[String], jsonMap.get(ROUTER).get.as[String], jsonMap.get(CLIENT).get.as[String])
    }
  }

  def fenixRespToWHOGwResp(fenixMessage: FenixMessage, target: Option[(String, String)]): Try[WHOGatewayMessage] = Try(
    fenixMessage match {
      case FenixGetEntityResp(offset, topic, payload) => WHOGwFetchResp(topic, Json.toJson(payload.mapValues(JsonUtil.parseAny)), target.get)
      case FenixOutputEntityResp(_, topic, payload, _) => WHOGwFetchResp(topic, Json.toJson(payload.mapValues(JsonUtil.parseAny)), target.get)
      case FenixOutputEntityRespAndSubscribe(_, topic, payload, _) => WHOGwSubscribeResp(topic, FenixSerializer.serializeJson(fenixMessage), target)
      case FenixCreateEntityEvent(_, topic, payload) => WHOGwSubscribeResp(topic, FenixSerializer.serializeJson(fenixMessage), target)
      case FenixMergeEntityEvent(_, topic, delta) => WHOGwSubscribeResp(topic, FenixSerializer.serializeJson(fenixMessage), target)
      case FenixUpdateEvent(_, topic, field, value) => WHOGwSubscribeResp(topic, FenixSerializer.serializeJson(fenixMessage), target)
      case FenixRemoveEvent(_, topic, field) => WHOGwSubscribeResp(topic, FenixSerializer.serializeJson(fenixMessage), target)
      case FenixClearEvent(_, topic) => WHOGwSubscribeResp(topic, FenixSerializer.serializeJson(fenixMessage), target)
      case FenixAddEvent(_, topic, field, values) => WHOGwSubscribeResp(topic, FenixSerializer.serializeJson(fenixMessage), target)
      case FenixDelEvent(_, topic, field, values) => WHOGwSubscribeResp(topic, FenixSerializer.serializeJson(fenixMessage), target)
    }
  )

  def serialize(in: WHOGatewayMessage): Try[String] = Try(Json.stringify(in.toJson))

}

/**
 * WHO Gateway Messages
 */

trait WHOGatewayMessage {
  val command: Int

  def toJson: JsValue
}

object WHOGatewayMessageConstants {
  val COMMAND = "C"
  val KEY = "K"
  val PAYLOAD = "P"
  val ROUTER = "R"
  val CLIENT = "S"
  val TIMESTAMP = "T"
  val ACTION = "A"

  val ACTION_BROADCAST = 0
  val ACTION_DIRECT = 1
}

/**
 * FETCH
 */

case class WHOGwFetchReq(resource: String, routerId: String, clientId: String) extends WHOGatewayMessage {

  import WHOGatewayMessageConstants._

  val command = 2

  def toJson: JsValue = Json.toJson(Map(COMMAND -> JsNumber(command), KEY -> JsString(resource), ROUTER -> JsString(routerId), CLIENT -> JsString(clientId)))
}

case class WHOGwFetchResp(resource: String, payload: JsValue, target: (String, String)) extends WHOGatewayMessage {

  import WHOGatewayMessageConstants._

  val command = 2

  def toJson: JsValue = Json.toJson(Map(COMMAND -> JsNumber(command), KEY -> JsString(resource), PAYLOAD -> payload, ROUTER -> JsString(target._1), CLIENT -> JsString(target._2)))
}

/**
 * SUBSCRIPTION
 */

case class WHOGwSubscribeReq(resource: String, routerId: String, clientId: String) extends WHOGatewayMessage {

  import WHOGatewayMessageConstants._

  val command = 3

  def toJson: JsValue = Json.toJson(Map(COMMAND -> JsNumber(command), KEY -> JsString(resource), ROUTER -> JsString(routerId), CLIENT -> JsString(clientId)))
}

case class WHOGwSubscribeResp(resource: String, payload: JsValue, target: Option[(String, String)]) extends WHOGatewayMessage {

  import WHOGatewayMessageConstants._

  val command = 3

  def toJson: JsValue = target match {
    case Some((routerId, clientId)) => Json.toJson(Map(COMMAND -> JsNumber(command), KEY -> JsString(resource), PAYLOAD -> payload, ROUTER -> JsString(routerId), CLIENT -> JsString(clientId), ACTION -> JsNumber(ACTION_DIRECT)))
    case None => Json.toJson(Map(COMMAND -> JsNumber(command), KEY -> JsString(resource), PAYLOAD -> payload, ACTION -> JsNumber(ACTION_BROADCAST)))
  }
}

case class WHOGwUnsubscribeReq(resource: String, routerId: String, clientId: String) extends WHOGatewayMessage {

  import WHOGatewayMessageConstants._

  val command = 4

  def toJson: JsValue = Json.toJson(Map(COMMAND -> JsNumber(command), KEY -> JsString(resource), ROUTER -> JsString(routerId), CLIENT -> JsString(clientId)))
}
