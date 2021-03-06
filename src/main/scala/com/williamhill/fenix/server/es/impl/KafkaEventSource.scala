package com.williamhill.fenix.server.es.impl

import java.time.Duration
import java.time.temporal.ChronoUnit.MILLIS
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.williamhill.fenix.server.es.EventSource
import org.apache.kafka.clients.consumer.KafkaConsumer
import rx.lang.scala.Observable
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

class KafkaEventSource(settings: Config) extends EventSource(settings) with LazyLogging {

  override def createSource(): Observable[String] = Observable.apply[String] { observer =>
    val topics = settings.getStringList("topics")
    val consumer = new KafkaConsumer[String, String](convertToMap(settings).asInstanceOf[Map[String, Object]].asJava)
    consumer.subscribe(topics)
    import ExecutionContext.Implicits.global
    var running = true

    val _ = Future {
      while (running) {
        try {
          val records = consumer.poll(Duration.of(100L, MILLIS)).asScala
          records.foreach(record => observer.onNext(record.value))
        } catch {
          case err: Throwable =>
            running = false
            observer.onError(err)
        }
      }
    }
  }

  private def convertToMap(config: Config): Map[String, String] =
    config.entrySet().asScala.map(entry => (entry.getKey, entry.getValue.unwrapped().toString)).toMap

}