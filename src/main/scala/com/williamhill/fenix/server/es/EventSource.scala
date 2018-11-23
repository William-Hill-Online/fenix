package com.williamhill.fenix.server.es

import rx.lang.scala.Observable
import com.williamhill.fenix.server.messages.FenixMessage
import com.typesafe.config.Config
import scala.util.Try

abstract class EventSource(settings: Config) {

  def createSource(): Observable[Any]

}

case class EventSourceDefinition(
  id:         String,
  active:     Boolean,
  provider:   EventSource,
  translator: Any => Try[List[FenixMessage]],
  settings:   Config
)