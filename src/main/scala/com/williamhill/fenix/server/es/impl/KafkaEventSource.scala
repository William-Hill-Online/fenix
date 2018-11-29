package com.williamhill.fenix.server.es.impl

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.williamhill.fenix.server.es.EventSource
import org.apache.kafka.clients.consumer.KafkaConsumer
import rx.lang.scala.{ Observable, Subscription }

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

class KafkaEventSource(settings: Config) extends EventSource(settings) with LazyLogging {

  override def createSource(): Observable[String] = Observable.create[String] { observer =>
    val topics = settings.getStringList("topics")
    val consumer = new KafkaConsumer[String, String](convertToMap(settings).asInstanceOf[Map[String, Object]].asJava)
    consumer.subscribe(topics)
    import ExecutionContext.Implicits.global
    var running = true

    Future {
      while (running) {
        try {
          val records = consumer.poll(100).asScala
          records.foreach(record => observer.onNext(record.value))
        } catch {
          case err: Throwable =>
            running = false
            observer.onError(err)
        }
      }
    }

    Subscription {
      running = false
      consumer.unsubscribe()
      consumer.close()
    }
  }

  private def convertToMap(config: Config): Map[String, String] =
    config.entrySet().asScala.map(entry => (entry.getKey, entry.getValue.unwrapped().toString)).toMap

}