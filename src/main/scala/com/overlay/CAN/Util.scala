package com.overlay.CAN

import java.security.MessageDigest
import java.math.BigInteger
import java.util.Base64
import akka.actor.typed.ActorRef
import com.overlay.CAN._
import com.overlay.CAN.Node._
import com.overlay.CAN.Interval
import com.typesafe.config.ConfigFactory
import java.io._
import java.nio.ByteBuffer
import scala.util.control.Breaks
import java.nio.ByteOrder
import scala.math.abs
object Util {
  
  val hashLength = 128

  val config = ConfigFactory.load("config_input_one")
  val outputPath = "./logs.yaml"
  val resultOutPath = "./results.yaml"

  def integerRangeOfBits(bits:Long): Interval = 
    Interval(  (scala.math.pow(2,bits)).toLong* (-1), ((scala.math.pow(2,bits).toLong-1)))

  def midpoint(x_1:Long, x_2:Long) =
    (x_1 + x_2) / 2

  def midpointOfInterval(p:Interval) = 
      midpoint(p.min,p.max)

  def midpointOfIntervals(intervals: Array[Interval]) = 
      intervals.map(midpointOfInterval(_))

  def manhattenDistance(p1: Array[Interval], p2: Array[Interval]):Long = {
    p1.map(midpointOfInterval).zip(p2.map(midpointOfInterval)).map{ case (x_1,x_2) => (x_1 - x_2).toLong.abs}.sum
  } 
  def manhattenDistance(p1: Array[Long], p2: Array[Long]):Long = {
    p1.zip(p2).map{ case (x_1,x_2) => (x_1 - x_2).toLong.abs.toLong}.sum
  }
  def euclideanDistance(p1: Array[Long], p2: Array[Long]): Long = 
    scala.math.sqrt((p1 zip p2).map { case (x,y) => scala.math.pow(y - x, 2).toLong }.sum.toLong).toLong

  def dimensionToSplit(lastSplit: Int,dimensions:Int) = 
    (lastSplit + 1) % dimensions 

  
  def splitZone(zone: Array[Interval],lastSplit: Int, dimensions:Int): (Interval,Interval)= {
    val dToSplit = dimensionToSplit(lastSplit,dimensions) 
    val d = zone(dToSplit)
    val (min, max) = (d.min, d.max)
    val mid = midpointOfInterval(d)

   (Interval(min, mid), Interval(mid+1,max))
  }
  def md5(s: String): Array[Byte] =
    MessageDigest.getInstance("MD5").digest(s.getBytes)

  def sha1(s: String): Array[Byte] =
    MessageDigest.getInstance("SHA-1").digest(s.getBytes)

  // Returns Unsigned Int from Byte Array
  def bigIntofArrayBytes(hash: Array[Byte]): BigInt = {
    BigInt(new BigInteger(1, hash))
  }

  def pointOfHash(hash: Array[Byte]): Array[Long] ={
    // for non perfect divisors of 128, the splitting of the hash into coordinates requires unequal lengths of the coordinates
    // making things more complicated, so limiting dimensions to powers of 2 up to 128 for right now.
    hash.grouped(hashLength/(Simulator.dimensions*8)).toArray.map(ByteBuffer.wrap(_).order(ByteOrder.LITTLE_ENDIAN).getLong()) 
  } 
  def inRange(x: (Long,Interval)) = x._2.min <= x._1 && x._2.max > x._1
  
  def inInterval(x: Array[Long], zone: Array[Interval]) ={
    x.zip(zone).forall(inRange(_)) 
  }

  def giveStateLogString(key: String, hash: Array[Byte]): String =
    ("[Key,Hash]: " + "[" + key + ", " + stringOfBytes(hash) + "]")

 def stringOfBytes(s: Array[Byte]): String = {
    if (s == null) return ""
    val sb = new StringBuffer();
    (1 to s.length - 1).foreach(i =>
      sb.append(Integer.toString((s(i) & 0xff) + 0x100, 16).substring(1))
    )
    sb.toString()
  }
  def inRange(x: Long, leftLimit: Long, rightLimit: Long, leftInclusive:Boolean, rightInclusive:Boolean): Boolean = {
    // If the interval is inclusive and the value is equal to either limit, return true
    if ((leftInclusive && x == leftLimit) || (rightInclusive && x == rightLimit))
      return true

    // Normal Case, left limit is less than right limit
    if (leftLimit <= rightLimit) {
      // x is greater than left limit and less than right limit return true
      if (x > leftLimit && x < rightLimit)
        return true
    }
    // Case where interval ranges over 0
    else {
      // x is greater than leftlimit or less than right limit return true. We know that all values involved are unsigned, minimum value is 0
      if (x > leftLimit || x < rightLimit)
        return true
    }
    false
  }

  def stringOfIntervals(intervals: Array[Interval], ws: Int = 2): String = {
    val sb = new StringBuilder()
    (0 until intervals.length) foreach (i =>
      sb.append(
        s"\n${(1 to ws).foldLeft("")((s,i)=> s+" ")  }Interval-for-Dimension: $i \n     Interval: (${intervals(i).min}, ${intervals(i).max})"
      )
    )
    sb.toString
  }
  def stringofNeighbors(key: String, ft: Array[Neighbor]): String = {

    var outString =
      s"\nNode ${key} Routing Table ===================================\n"
    (0 until ft.length) foreach (i =>
      outString += s" Neighbors: $i \n  ${stringOfIntervals(ft(i).intervals)}"
    )

    outString + "============================================================"
  }
  def yamlDump(logs: String): Unit = {
    val yamlFileWriter = new BufferedWriter(
      new FileWriter(new File(outputPath), true)
    )
    yamlFileWriter.write(logs)
    yamlFileWriter.close()
  }
  def yamlDumpResults(logs: String): Unit = {
    val yamlFileWriter = new BufferedWriter(
      new FileWriter(new File(resultOutPath), true)
    )
    yamlFileWriter.write(logs)
    yamlFileWriter.close()
  }

}
