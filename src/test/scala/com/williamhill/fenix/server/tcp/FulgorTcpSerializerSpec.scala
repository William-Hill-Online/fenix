package com.williamhill.fenix.server.tcp

import com.williamhill.fenix.server.messages._

class FenixTcpSerializerSpec {

  val create = FenixCreateEntityCmd("OB_EV54952", Map[String, String]("name" -> "FC Juventus vs Manchester United", "inPlay" -> "false"))
  val createStr = FenixSerializer.serialize(create).utf8String
  println(createStr)

  val update = FenixUpdateCmd("OB_EV54952", "inPlay", "true")
  val updateStr = FenixSerializer.serialize(update).utf8String
  println(updateStr)

  val getEntity = FenixGetEntityReq("OB_EV54952")
  val getEntityStr = FenixSerializer.serialize(getEntity).utf8String
  println(getEntityStr)

  val subscribe = FenixTopicSubscribe("OB_EV54952")
  val subscribeStr = FenixSerializer.serialize(subscribe).utf8String
  println(subscribeStr)

}