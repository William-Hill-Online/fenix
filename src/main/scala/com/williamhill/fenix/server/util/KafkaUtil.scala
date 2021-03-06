package com.williamhill.fenix.server.util

import java.time.Duration
import java.time.temporal.ChronoUnit.MILLIS

import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.KafkaConsumer
import rx.lang.scala.Observable

import collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

object KafkaUtil {

  def createSource(inputStreams: Seq[String], settings: Config, pollInterval: Long): Observable[String] = Observable.apply[String] { observer =>
    val consumer = new KafkaConsumer[String, String](ConfigUtil.convertToMap(settings).asJava)
    consumer.subscribe(inputStreams.asJava)
    import ExecutionContext.Implicits.global
    var running = true

    val _ = Future {
      while (running) {
        try {
          val records = consumer.poll(Duration.of(pollInterval, MILLIS)).asScala
          records.foreach(record => observer.onNext(record.value))
        } catch {
          case err: Throwable =>
            running = false
            observer.onError(err)
        }
      }
      consumer.unsubscribe()
      consumer.close()
    }
  }

}
