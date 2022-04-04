package com.overlay

import com.overlay.Util._
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import akka.actor.typed.ActorRef
import com.overlay.Node.NodeCommand

class UtilTest extends AnyFunSuite {
  // SLF4J Logger Creation
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  // Test Verifying Normal hashVal Case
  test("hashVal() Normal Expected Behavior") {
    // 128 bit value has 16 bytes
    val normalBArr:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 100.toByte, 255.toByte)
    // Assert that byte array is interpreted as correct value
    assert(BigInt(25855) == hashVal(normalBArr))
  }
  // Test Verifying Negative hashVal Case
  test("hashVal() Negative Value Expected Behavior") {
    // Byte arrays with leading 1 are normally interpreted as negative values
    val negativeBArr:Array[Byte] = Array(255.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte,  0.toByte, 0.toByte, 0.toByte, 0.toByte,  0.toByte, 0.toByte,  0.toByte, 0.toByte, 0.toByte, 0.toByte)
    // Confirm that normally negative byte array is positive value
    assert(hashVal(negativeBArr) > 0)
  }

  // Test Verifying Normal inInterval Case
  test("inInterval() Normal Expected Behavior") {
    val id:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 26.toByte)
    val lim1:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 8.toByte)
    val lim2:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 50.toByte)
    val lim3:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 20.toByte)
    // Test inclusive edge cases
    assert(inInterval(hashVal(id), hashVal(lim1), hashVal(id), false, true))
    assert(inInterval(hashVal(id), hashVal(id), hashVal(lim2), true, false))

    // Test exclusive positive case
    assert(inInterval(hashVal(id), hashVal(lim1), hashVal(lim2), false, false))

    // Test exclusive negative case
    assert(!inInterval(hashVal(id), hashVal(lim1), hashVal(lim3), false, false))

  }

  // Test Verifying Over 0 inInterval Case
  test("inInterval() Over 0 Expected Behavior") {
    val id:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 26.toByte)
    // Very large value, counterclockwise of top of ring
    val lim1:Array[Byte] = Array(255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 0.toByte, 0.toByte, 0.toByte)
    // Small value, clockwise of top of ring
    val lim2:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 50.toByte)
    val lim3:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 20.toByte)
    // Inclusive cases with interval containing 0
    assert(inInterval(hashVal(id), hashVal(lim1), hashVal(id), false, true))
    assert(inInterval(hashVal(id), hashVal(id), hashVal(lim3), true, false))

    // Exclusive cases with interval containing 0
    assert(inInterval(hashVal(id), hashVal(lim1), hashVal(lim2), false, false))
    assert(!inInterval(hashVal(id), hashVal(lim1), hashVal(lim3), false, false))
  }

  // Test Verifying Normal ringDistance Case
  test("ringDistance() Normal Case Expected Behavior") {
    val point1: BigInt = hashVal(Array(40.toByte))
    val point2: BigInt = hashVal(Array(50.toByte))
    assert(ringDistance(point1, point2, 128) == hashVal(Array(10.toByte)))
  }

  // Test Verifying Over 0 ringDistance Case
  test("ringDistance() Over 0 Expected Behavior") {
    val point1: BigInt = hashVal(Array(50.toByte))
    val point2: BigInt = hashVal(Array(40.toByte))
    // If the difference to check goes over 0, the max value minux the distance between the points should be the distance between them in the other direction
    assert((BigInt(2).pow(128) - 1) - ringDistance(point1, point2, 128) == BigInt(Array(10.toByte)))
  }

  // Test Verifying Lone Node Behavior
  test("tryFindSuccessor() with one node expected behavior") {
    // Number of bits in hash
    val m = 128
    // Hash and Finger Table of lone node
    val hash:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 50.toByte)
    val ft: IndexedSeq[(Array[Byte], Array[Byte], ActorRef[Node.NodeCommand])] =
      (0 until m).map[(Array[Byte], Array[Byte], ActorRef[Node.NodeCommand])](
        i => Tuple3((hashVal(hash) + BigInt(2).pow(i)).mod(BigInt(2).pow(m) - 1).toByteArray, hash, null))

    // Both larger and smaller ids should have the lone node as the successor
    val largerId:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 255.toByte)
    val smallerId:Array[Byte] = Array(0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, 15.toByte)

    // Both requests should have found the current node as the successor
    assert(tryFindSuccessor(largerId, hash, null, ft) == (true, hash, null))
    assert(tryFindSuccessor(smallerId, hash, null, ft) == (true, hash, null))
  }

  // Test Verifying Normal Node Success Behavior
  test("tryFindSuccessor() with normal network expected successes") {
    // Number of bits in hash
    val m = 128
    // Hash and Finger Table of Current Node
    val node1Hash = BigInt(50).toByteArray
    val node2Hash = BigInt(3050).toByteArray
    val node3Hash = BigInt(65600).toByteArray
    val node4Hash = BigInt(2097300).toByteArray

    // Manually create finger table
    val ft = (0 until 12).map[(Array[Byte], Array[Byte], ActorRef[Node.NodeCommand])](
        i => Tuple3((hashVal(node1Hash) + BigInt(2).pow(i)).mod(BigInt(2).pow(m) - 1).toByteArray, node2Hash, null)) ++
            (12 until 17).map[(Array[Byte], Array[Byte], ActorRef[Node.NodeCommand])](
        i => Tuple3((hashVal(node1Hash) + BigInt(2).pow(i)).mod(BigInt(2).pow(m) - 1).toByteArray, node3Hash, null)) ++
            (17 until 22).map[(Array[Byte], Array[Byte], ActorRef[Node.NodeCommand])](
        i => Tuple3((hashVal(node1Hash) + BigInt(2).pow(i)).mod(BigInt(2).pow(m) - 1).toByteArray, node4Hash, null)) ++
            (22 until m).map[(Array[Byte], Array[Byte], ActorRef[Node.NodeCommand])](
        i => Tuple3((hashVal(node1Hash) + BigInt(2).pow(i)).mod(BigInt(2).pow(m) - 1).toByteArray, node1Hash, null))

    // All these values should have a successor value of node 3050, node2Hash
    val idNorm = BigInt(2345).toByteArray
    val idLowBound = BigInt(50).toByteArray
    val idUpBound = BigInt(3049).toByteArray

    // Assert that successors of above nodes are successful and equal to node2Hash
    assert(tryFindSuccessor(idNorm, node1Hash, null, ft) == (true, node2Hash, null))
    assert(tryFindSuccessor(idLowBound, node1Hash, null, ft) == (true, node2Hash, null))
    assert(tryFindSuccessor(idUpBound, node1Hash, null, ft) == (true, node2Hash, null))
  }

  // Test Verifying Normal Node Failure Behavior
  test("tryFindSuccessor() with normal network expected failures") {
    // Number of bits in hash
    val m = 128
    // Hash and Finger Table of Current Node
    val node1Hash = BigInt(50).toByteArray
    val node2Hash = BigInt(3050).toByteArray
    val node3Hash = BigInt(65600).toByteArray
    val node4Hash = BigInt(2097300).toByteArray

    // Manually create finger table
    val ft = (0 until 12).map[(Array[Byte], Array[Byte], ActorRef[Node.NodeCommand])](
      i => Tuple3((hashVal(node1Hash) + BigInt(2).pow(i)).mod(BigInt(2).pow(m) - 1).toByteArray, node2Hash, null)) ++
      (12 until 17).map[(Array[Byte], Array[Byte], ActorRef[Node.NodeCommand])](
        i => Tuple3((hashVal(node1Hash) + BigInt(2).pow(i)).mod(BigInt(2).pow(m) - 1).toByteArray, node3Hash, null)) ++
      (17 until 22).map[(Array[Byte], Array[Byte], ActorRef[Node.NodeCommand])](
        i => Tuple3((hashVal(node1Hash) + BigInt(2).pow(i)).mod(BigInt(2).pow(m) - 1).toByteArray, node4Hash, null)) ++
      (22 until m).map[(Array[Byte], Array[Byte], ActorRef[Node.NodeCommand])](
        i => Tuple3((hashVal(node1Hash) + BigInt(2).pow(i)).mod(BigInt(2).pow(m) - 1).toByteArray, node1Hash, null))

    // These values should not find a successor, and the closest preceding finger should be node4
    val idBehind = BigInt(6).toByteArray
    val idBehindZero = Array(255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 255.toByte, 0.toByte, 0.toByte, 0.toByte)
    // These values should not find a successor, and the closest preceding finger should be node3
    val idAhead = BigInt(89300).toByteArray
    // Boundary Cases
    // Closest Preceding Finger node4
    val idLowBound = BigInt(49).toByteArray
    // Closest Preceding Finger node2
    val idUpBound = BigInt(3050).toByteArray

    // Assert that successors of above nodes are successful and equal to node2Hash
    assert(tryFindSuccessor(idBehind, node1Hash, null, ft) == (false, node4Hash, null))
    assert(tryFindSuccessor(idBehindZero, node1Hash, null, ft) == (false, node4Hash, null))
    assert(tryFindSuccessor(idAhead, node1Hash, null, ft) == (false, node3Hash, null))
    assert(tryFindSuccessor(idLowBound, node1Hash, null, ft) == (false, node4Hash, null))
    assert(tryFindSuccessor(idUpBound, node1Hash, null, ft) == (false, node2Hash, null))
  }
}
