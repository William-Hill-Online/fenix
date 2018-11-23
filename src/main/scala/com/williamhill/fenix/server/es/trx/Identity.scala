package com.williamhill.fenix.server.es.trx

import akka.util.ByteString
import com.williamhill.fenix.server.messages.{FenixMessage, FenixSerializer}

import scala.util.Try

class Identity() extends (Any => Try[List[FenixMessage]]) {

  override def apply(input: Any): Try[List[FenixMessage]] =
    FenixSerializer.parse(ByteString(input.toString)).map(List(_))

}
