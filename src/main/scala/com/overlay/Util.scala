package com.overlay

import java.security.MessageDigest
import java.math.BigInteger
import java.util.Base64
import akka.actor.typed.ActorRef
import com.overlay.Node.NodeCommand

import com.typesafe.config.ConfigFactory
import java.io._

import scala.util.control.Breaks

object Util {

  val config = ConfigFactory.load("config_input_one")
  val outputPath = "./logs.yaml"
  val resultOutPath = "./results.yaml"

  def stringOfBytes(s: Array[Byte]): String = {
    if(s == null) return ""
    val sb = new StringBuffer();
    (1 to s.length - 1).foreach(i =>
      sb.append(Integer.toString((s(i) & 0xff) + 0x100, 16).substring(1))
    )
    sb.toString()
  }

  def md5(s: String): Array[Byte] =
    MessageDigest.getInstance("MD5").digest(s.getBytes)
  def sha1(s: String): Array[Byte] =
    MessageDigest.getInstance("SHA-1").digest(s.getBytes)
  def giveStateLogString(key: String, hash: Array[Byte]): String =
    ("[Key,Hash]: " + "[" + key + ", " + stringOfBytes(hash) + "]")

  // Returns Unsigned Int from Byte Array
  def hashVal(hash: Array[Byte]): BigInt = {
    BigInt(new BigInteger(1, hash))
  }

  def inInterval(x: BigInt, leftLimit: BigInt, rightLimit: BigInt, leftInclusive:Boolean, rightInclusive:Boolean): Boolean = {
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

  // Returns true and the successor if found
  // Returns false and the closest preceding finger otherwise
  // Combines logic of find_successor() and closest_preceding_finger()
  def tryFindSuccessor(id: Array[Byte], currentNodeHash: Array[Byte], currentNodeRef: ActorRef[NodeCommand], fingerTable: IndexedSeq[(Array[Byte], Array[Byte], ActorRef[NodeCommand])]): (Boolean, Array[Byte], ActorRef[NodeCommand]) = {
    // If there is more than one node in the network or the id is not between current node and successor
    // Then current node is predecessor, this node's successor is the succeesor
    if (!(hashVal(currentNodeHash) == hashVal(fingerTable(0)._2) || inInterval(hashVal(id), hashVal(currentNodeHash), hashVal(fingerTable(0)._2), true, false))) {
      val m = fingerTable.length
      for (i <- (m - 1) to 0 by -1) {
        // Send message onto closest preceding finger table entry, if between this node and id
        if (fingerTable(i)._2 != null && inInterval(hashVal(fingerTable(i)._2), hashVal(currentNodeHash), hashVal(id), false, true)) {
          return (false, fingerTable(i)._2, fingerTable(i)._3)
        }
      }
    }
    (true, fingerTable(0)._2, fingerTable(0)._3)
  }

  def ringDistance(x: BigInt, y: BigInt, m: Int): BigInt = {
    // if distance passes over 0, then add the distance from x to the top, to the top to y, absolute value
    if (x > y)
      return ((BigInt(2).pow(m) - 1) - x) + y
    y - x
  }

  def ftString(key: String, ft:IndexedSeq[(Array[Byte], Array[Byte], ActorRef[NodeCommand])]): String = {
    var outString = s"\nNODE ${key} FINGER TABLE ===================================\n"
    (0 until ft.length).foreach(j => outString += s"${j}\tSTART: ${if (ft(j)._1 != null) hashVal(ft(j)._1) else "null"} NODE: ${if (ft(j)._2 != null) hashVal(ft(j)._2) else "null"} REF: ${if (ft(j)._3 != null) ft(j)._3 else "null"}\n")
    outString + "============================================================"
  }
  def yamlDump(logs:String ): Unit= {
    val yamlFileWriter = new BufferedWriter(new FileWriter(new File(outputPath), true))
    yamlFileWriter.write(logs) 
    yamlFileWriter.close()
  }
  def yamlDumpResults(logs:String): Unit = {
    val yamlFileWriter = new BufferedWriter(new FileWriter(new File(resultOutPath), true))
    yamlFileWriter.write(logs) 
    yamlFileWriter.close()
  }

}
