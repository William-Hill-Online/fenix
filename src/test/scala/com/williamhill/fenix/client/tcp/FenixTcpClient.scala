package com.williamhill.fenix.client.tcp

import java.io.{BufferedReader, DataInputStream, DataOutputStream, InputStreamReader}
import java.net.Socket
import java.nio.ByteBuffer

import akka.util.ByteString

import scala.concurrent.Future

object FenixTcpClient {
  def main(args: Array[String]): Unit = {
    val socket = new Socket("192.168.99.100", 7777)
    val out = new DataOutputStream(socket.getOutputStream)
    val stdIn = new BufferedReader(new InputStreamReader(System.in))
    var userInput = ""

    Future {
      var position: Int = 0
      var sockInput: Byte = 0.toByte
      var bs = List[Byte]()
      val in = new DataInputStream(socket.getInputStream)
      System.err.println("Listening")
      sockInput = in.readByte()
      position += 1
      while (sockInput != -1) {
        bs = bs ++ List(sockInput)
        if (position > 4) {
          val size = ByteBuffer.wrap(bs.take(4).toArray).getInt
          if (position == 4 + size) {
            System.err.println("Read: " + ByteString(bs.drop(4).toArray).utf8String)
            position = 0
            bs = List()
          }
        }
        sockInput = in.readByte()
        position += 1
      }
      System.err.println("finished")
      System.exit(0)
    }

    do {
      val userInput = stdIn.readLine()
      val lengthArray = ByteBuffer.allocate(4).putInt(userInput.length).array()
      val toSend = lengthArray ++ userInput.getBytes
      out.write(toSend)
      //      out.write(util.Arrays.copyOfRange(toSend, 0, 4))
      //      out.write(util.Arrays.copyOfRange(toSend, 4, toSend.length))
    } while (userInput != null)
  }
}
