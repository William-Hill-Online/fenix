package com.williamhill.fenix.server.util

import play.api.libs.json.{JsValue, Json}

object JsonUtil {

  def parseAny: PartialFunction[Any, JsValue] = {
    case js: JsValue => js
    case elements: Seq[Any] => Json.toJson(elements.map(parseAny))
    case str: String => Json.toJson(str)
    case bool: Boolean => Json.toJson(bool)
    case num: Int => Json.toJson(num)
    case num: Long => Json.toJson(num)
    case num: Double => Json.toJson(num)
  }

}
