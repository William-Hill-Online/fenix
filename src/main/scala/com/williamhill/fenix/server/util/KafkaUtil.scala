package com.williamhill.fenix.server.util

import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.KafkaConsumer
import rx.lang.scala.{ Observable, Subscription }
import collection.JavaConverters._

import scala.concurrent.{ ExecutionContext, Future }

object KafkaUtil {

  def createSource(inputStreams: Seq[String], settings: Config, pollInterval: Long): Observable[String] = Observable.apply[String] { observer =>
    val consumer = new KafkaConsumer[String, String](ConfigUtil.convertToMap(settings).asJava)
    consumer.subscribe(inputStreams.asJava)
    import ExecutionContext.Implicits.global
    var running = true

    Future {
      while (running) {
        try {
          val records = consumer.poll(pollInterval).asScala
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
