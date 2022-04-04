package com.overlay
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import com.overlay.Simulator.SimulatorCommand
import com.overlay.Util._
import com.overlay.Bootstrapper.OverlayCommand
import com.overlay.Simulator.SimulatorCommand

import scala.concurrent.duration.DurationInt
import scala.util.control.Breaks
import scala.util.control.Breaks.break
import akka.util.Index

object Node {
  trait NodeCommand extends SimulatorCommand
  final case class Say(word: String) extends NodeCommand
  final case class GiveState() extends NodeCommand
  // This message checks if the node is active
  final case class ActiveCheck(replyTo: ActorRef[Boolean]) extends NodeCommand
  final case class ExistingNodeRef(replyTo: ActorRef[NodeCommand])
      extends NodeCommand
  // This message is sent to determine the predecessor and successor of id
  // replyTo is original sender, id is search id, i for index into ft, -1 if n/a
  final case class FindPredSucc(
      replyTo: ActorRef[NodeCommand],
      id: Array[Byte],
      i: Int
  ) extends NodeCommand
  // This message is the result of a query for predecessor and successor
  // pred, succ are predecessor and successor ft entries without start
  final case class FoundPredSucc(
      newPred: (Array[Byte], ActorRef[NodeCommand]),
      newSucc: (Array[Byte], ActorRef[NodeCommand]),
      i: Int
  ) extends NodeCommand
  // Following message is logic of both update_finger_table and update_others
  // Updates possible predecessor of new node, with node and ref for ft index i
  final case class UpdateIfPredecessor(
      id: Array[Byte],
      node: Array[Byte],
      ref: ActorRef[NodeCommand],
      index: Int
  ) extends NodeCommand
  // Update finger table with entry in parameters
  final case class UpdateFingerTable(
      node: Array[Byte],
      ref: ActorRef[NodeCommand],
      index: Int
  ) extends NodeCommand
  final case class Notify(
      senderHash: Array[Byte],
      senderRef: ActorRef[NodeCommand]
  ) extends NodeCommand
  final case class Stabilize(
      senderHash: Array[Byte],
      senderRef: ActorRef[NodeCommand]
  ) extends NodeCommand
  final case class FixRandomFinger() extends NodeCommand
  final case class BeginStabilizeSchedule() extends NodeCommand
  final case class AddMovie(
       movie: Movie,
      replyTo: ActorRef[SimulatorCommand],
      forceReceive: Boolean,
      startTime:Long
  ) extends NodeCommand
  final case class GetMovie(
      name: String,
      replyTo: ActorRef[Simulator.MovieResponse],
      mustHave: Boolean,
      startTime:Long
  ) extends NodeCommand

  final val m = 128
  final case class IAmNew(selfRef: ActorRef[NodeCommand]) extends NodeCommand
  final case class Pause(replyTo: ActorRef[Bootstrapper.OverlayCommand])
      extends NodeCommand
  final case class WriteLogs(replyTo: ActorRef[Bootstrapper.OverlayCommand])
      extends NodeCommand
  final case class Resume() extends NodeCommand
  final case class StopNode() extends NodeCommand
  final case class RemoveMe(dropNode: Array[Byte]) extends NodeCommand
  final case class UpdateIfHasRemoved(id: Array[Byte], dropNode: Array[Byte]) extends NodeCommand

  // Node variables (key, hash, finger, pred, active)
  // Finger table entries (start, node, ref)

  def apply(
      key: String,
      hash: Array[Byte],
      fingerTable: IndexedSeq[
        (Array[Byte], Array[Byte], ActorRef[NodeCommand])
      ],
      pred: (Array[Byte], ActorRef[NodeCommand]),
      active: Boolean,
      movies: IndexedSeq[Movie]
  ): Behavior[NodeCommand] = {
    node(key, hash, fingerTable, pred, active, movies)
  }

  def node(
      key: String,
      hash: Array[Byte],
      fingerTable: IndexedSeq[
        (Array[Byte], Array[Byte], ActorRef[NodeCommand])
      ],
      pred: (Array[Byte], ActorRef[NodeCommand]),
      active: Boolean,
      movies: IndexedSeq[Movie]
  ): Behavior[NodeCommand] =
    Behaviors.receive {
      case (context, GetMovie(name, replyTo, mustHave,startTime)) =>
        // Movie should be stored in this node if flag says so
        if (mustHave) {
          // Reply to requestor with movie, if we have it
          val storedMovie = movies.find(movie => movie.title == name)
          if (replyTo != null)
            replyTo ! Simulator.MovieResponse(storedMovie)
          storedMovie match {
            // case None => Util.yamlDumpResults(s"\n  Read-failed:\n    Movie:${name}")
            case _ => Util.yamlDumpResults(s"\n  Read-Request:\n    Movie: ${name}\n    Time: ${System.currentTimeMillis() - startTime}ms")
          }
          Behaviors.same
        }
        // We need to find what node should be responsible for nameHashVal, sucessor of nameHashVal
        else {
          // Hash of movie name
          val movieHash = Util.md5(name)
          // Find either successor or closest preceding finger
          val findSuccAttempt: (Boolean, Array[Byte], ActorRef[NodeCommand])= tryFindSuccessor(movieHash, hash, context.self, fingerTable)
          // Successor has been found, tell successor to get this movie
          if (findSuccAttempt._1) {
            findSuccAttempt._3 ! GetMovie(name, replyTo, true, startTime)
          }
          // Successor was not found, ask closest preceding finger
          else {
            //Util.yamlDumpResults(s"  Successor-not-found: ${name replaceAll(":","")}\n")
            findSuccAttempt._3 ! GetMovie(name, replyTo, false,startTime)
          }
          Behaviors.same
        }
      case (context, AddMovie(movie, replyTo, mustStore,startTime)) =>
        if (!active) {
          // Send request to my successor, once the request comes back around maybe I'll be active or gone
          fingerTable(0)._3 ! AddMovie(movie, replyTo, false,startTime)
          Behaviors.same
        }
        else {
          // Movie should be stored in this node if flag says so
          if (mustStore) {
            // Reply if there is someone to reply to
            if (replyTo != null)
              replyTo ! Simulator.StoreResponse(movie.title, true)
            Util.yamlDumpResults(s"\n  Write-Request:\n    Movie: ${movie.title}\n    Time: ${System.currentTimeMillis() - startTime}ms")
            node(key, hash, fingerTable, pred, active, movies :+ movie)
          }
          // We need to find what node should be responsible for nameHashVal, sucessor of nameHashVal
          else {
            // Hash of movie name
            val movieHash = Util.md5(movie.title)
            // Find either successor or closest preceding finger
            val findSuccAttempt: (Boolean, Array[Byte], ActorRef[NodeCommand])= tryFindSuccessor(movieHash, hash, context.self, fingerTable)
            // Successor has been found, tell successor to add this movie
            if (findSuccAttempt._1) {
              findSuccAttempt._3 ! AddMovie(movie, replyTo, true,startTime)
            }
            // Successor was not found, ask closest preceding finger
            else {
              findSuccAttempt._3 ! AddMovie(movie, replyTo, false,startTime)
            }
            Behaviors.same
          }
        }
      case (context, GiveState()) =>
        val log = giveStateLogString(key, hash)
        context.log.info(s"Node${log}")
        val ftString = Util.ftString(key, fingerTable)
        //context.log.info(ftString)
        Behaviors.same
      case (context, ActiveCheck(replyTo)) =>
        replyTo ! active
        Behaviors.same
      case (context, Pause(replyTo)) =>
        context.log.info("pausing")
        context.log.info(s"Node ${key} pausing ...")
        replyTo ! Bootstrapper.ChildPause(context.self)
        pause(key, hash, fingerTable, pred, active, movies)
      case (context, ExistingNodeRef(ref)) =>
        // m is the number of bits in the hash, or bytes * 8
        // val m: Int = 128
        // If the given node reference does exist, new node joining
        if (ref != null) {
          // context.log.info(
          //   s"Node ${key} is not alone, finding predecessor and finger table"
          // )
          // Initialize finger table with correct start values and null node and ref fields
          val ft
              : IndexedSeq[(Array[Byte], Array[Byte], ActorRef[NodeCommand])] =
            (0 until m).map[(Array[Byte], Array[Byte], ActorRef[NodeCommand])](
              i =>
                Tuple3(
                  (hashVal(hash) + BigInt(2).pow(i))
                    .mod(BigInt(2).pow(m) - 1)
                    .toByteArray,
                  null,
                  null
                )
            )
          // Find this node's predecessor
          ref ! FindPredSucc(context.self, hash, -1)
          // Find this node's successor
          ref ! FindPredSucc(context.self, hash, 0)
          // Set this node with the empty finger table, no predecessor, and inactive
          node(key, hash, ft, null, false, movies)
        }
        // If the given node reference does not exist, this is the first node in network
        else {
          // context.log.info(
          //   s"Node ${key} is alone, initializes own finger table"
          // )
          // Initialize all finger table entries to self
          val ft
              : IndexedSeq[(Array[Byte], Array[Byte], ActorRef[NodeCommand])] =
            (0 until m).map[(Array[Byte], Array[Byte], ActorRef[NodeCommand])](
              i =>
                Tuple3(
                  (hashVal(hash) + BigInt(2).pow(i))
                    .mod(BigInt(2).pow(m) - 1)
                    .toByteArray,
                  hash,
                  context.self
                )
            )
          //context.log.info(ftString(key, ft))
          // Set this node with a full finger table, predecessor as itself, and active
          node(key, hash, ft, (hash, context.self), true, movies)
        }
      case (context, FindPredSucc(replyTo, id, index)) =>
        // Find either successor or closest preceding finger
        val findSuccAttempt: (Boolean, Array[Byte], ActorRef[NodeCommand])= tryFindSuccessor(id, hash, context.self, fingerTable)
        // Successor has been found, send myself and successor value
        if (findSuccAttempt._1) {
          replyTo ! FoundPredSucc((hash, context.self), (findSuccAttempt._2, findSuccAttempt._3), index)
        }
        // Successor was not found, ask closest preceding finger
        else {
          findSuccAttempt._3 ! FindPredSucc(replyTo, id, index)
        }
        Behaviors.same
      case (
            context,
            FoundPredSucc(
              newPred: (Array[Byte], ActorRef[NodeCommand]),
              newSucc: (Array[Byte], ActorRef[NodeCommand]),
              i: Int
            )
          ) =>
        if (i == -1) { // Means this node was looking for its predecessor, set predecessor to pred
          //context.log.info(s"Node ${key} received its predecessor!")
          //context.log.info(newPred.toString)
          val isActive = fingerTable.forall(e => e._2 != null && e._3 != null)
          node(key, hash, fingerTable, newPred, isActive, movies)
        } else { // Means this node was trying to update its finger table
          //context.log.info(s"Node ${key} received finger table entry ${i}\ni: ${newSucc._1}, ${newSucc._2}")
          // Update finger table with new entry
          var ftUpdate = fingerTable.patch(
            i,
            Seq((fingerTable(i)._1, newSucc._1, newSucc._2)),
            1
          )
          // Continue filling in finger table
          val loop = new Breaks
          loop.breakable {
            for (j <- i until (m - 1)) {
              // If start of next finger table entry is between [this node, received node)
              // and the existing finger entry is null, or existing entry is further away than new option
              if (
                inInterval(
                  hashVal(ftUpdate(j + 1)._1),
                  hashVal(hash),
                  hashVal(ftUpdate(j)._2),
                  true,
                  false
                ) &&
                (ftUpdate(j + 1)._2 == null || ringDistance(
                  hashVal(ftUpdate(j + 1)._1),
                  hashVal(ftUpdate(j + 1)._2),
                  m
                ) > ringDistance(
                  hashVal(ftUpdate(j + 1)._1),
                  hashVal(ftUpdate(j)._2),
                  m
                ))
              )
                ftUpdate = ftUpdate.patch(
                  j + 1,
                  Seq((ftUpdate(j + 1)._1, newSucc._1, newSucc._2)),
                  1
                )
              // Otherwise ask the new successor where this node is
              else {
                // If the next entry is not replaced and is still null, then find out what its successor is
                if (ftUpdate(j + 1)._2 == null)
                  newSucc._2 ! FindPredSucc(
                    context.self,
                    ftUpdate(j + 1)._1,
                    j + 1
                  )
                loop.break()
              }
            }
          }
          // Check if we can set this node to active after updating the finger table
          val isActive =
            ftUpdate.forall(e => e._2 != null && e._3 != null) && pred != null
          // Node's finger table is filled, the node is now active and can accept requests
          if (isActive && !active) {
            // context.log.info(s"Node ${key} is newly active!")
            //context.log.info(ftString(key, ftUpdate))

            // Update predecessors about me
            if (pred == null) {
            (0 until m).foreach(j =>
              fingerTable(0)._3 ! UpdateIfPredecessor(
                (hashVal(hash) - BigInt(2).pow(j))
                  .mod(BigInt(2).pow(m) - 1)
                  .toByteArray,
                hash,
                context.self,
                j
              )
            )
            }
            else {
              (0 until m).foreach(j =>
                pred._2 ! UpdateIfPredecessor(
                  (hashVal(hash) - BigInt(2).pow(j))
                    .mod(BigInt(2).pow(m) - 1)
                    .toByteArray,
                  hash,
                  context.self,
                  j
                )
              )
            }
            // Set Up Stabilization Schedule
            context.self ! BeginStabilizeSchedule()
            //TODO Move keys in (predecessor, n] from successor
          }
          node(key, hash, ftUpdate, pred, isActive, movies)
        }
      case (context, UpdateIfPredecessor(id: Array[Byte], newNode: Array[Byte], ref: ActorRef[NodeCommand], index: Int)) =>
        // Find either successor or closest preceding finger
        val findSuccAttempt: (Boolean, Array[Byte], ActorRef[NodeCommand])= tryFindSuccessor(id, hash, context.self, fingerTable)
        // Successor has been found, so I am predecessor. Tell myself to update finger table
        if (findSuccAttempt._1) {
          context.self ! UpdateFingerTable(newNode, ref, index)
        }
        // Predecessor was not found, ask closest preceding finger
        else {
          findSuccAttempt._3 ! UpdateIfPredecessor(id, newNode, ref, index)
        }
        Behaviors.same
      case (
            context,
            UpdateFingerTable(
              newNode: Array[Byte],
              ref: ActorRef[NodeCommand],
              index: Int
            )
          ) =>
        // New node is closer to fingerTable(i)._1 than current entry
        if (
          ringDistance(
            hashVal(fingerTable(index)._1),
            hashVal(newNode),
            m
          ) < ringDistance(
            hashVal(fingerTable(index)._1),
            hashVal(fingerTable(index)._2),
            m
          )
        ) {
          // context.log.info(s"Node ${key} was asked by node ${newNode} to update its finger table index ${index}")
          val ftUpdate = fingerTable.patch(
            index,
            Seq((fingerTable(index)._1, newNode, ref)),
            1
          )
          if (pred != null)
            pred._2 ! UpdateFingerTable(newNode, ref, index)
          node(key, hash, ftUpdate, pred, active, movies)
        } else
          Behaviors.same
      // Sender wants to know whether Recipient is its successor
      case (
            context,
            Stabilize(senderHash: Array[Byte], senderRef: ActorRef[NodeCommand])
          ) =>
        // If my predecessor is between the sender and me, then my predecessor is the sender's successor
        // Tell my predecessor that the sender may be its predecessor
        if (
          inInterval(
            hashVal(pred._1),
            hashVal(senderHash),
            hashVal(hash),
            leftInclusive = false,
            rightInclusive = false
          )
        ) {
          //context.log.info(s"Node ${key}'s predecessor is successor of asking Node ${senderHash}, notify my predecessor of sender node...")
          senderRef ! FoundPredSucc((null, null), pred, 0)
          pred._2 ! Notify(senderHash, senderRef)
        } else {
          //context.log.info("Notify myself of sender node")
          context.self ! Notify(senderHash, senderRef)
        }
        Behaviors.same

      // Sender thinks it might be Recipient's Predecessor
      case (
            context,
            Notify(senderHash: Array[Byte], senderRef: ActorRef[NodeCommand])
          ) =>
        // If I have no predecessor, or the sender is between my predecessor and me, then its my predecessor
        if (pred == null || inInterval(hashVal(senderHash), hashVal(pred._1), hashVal(hash), false, false)) {
          //context.log.info(s"Node ${key} notified of node ${senderHash}, it is my predecessor")
          // Update my predecessor
          context.self ! FoundPredSucc((senderHash, senderRef), (null, null), -1)
          // Give predecessor any keys it should be responsible for
          val predMovies = movies.filterNot(m => inInterval(hashVal(Util.md5(m.title)), hashVal(senderHash), hashVal(hash), false, true))
          val myMovies = movies.filter(m => inInterval(hashVal(Util.md5(m.title)), hashVal(senderHash), hashVal(hash), false, true))
          // Give predecessor all the movies
          predMovies.foreach(m => senderRef ! AddMovie(m, null, true,0))
          // context.log.info(s"Node [${key}] is updating its predecessor, sent predecessor ${predMovies.length} movies")
          // Update my movies
          node(key, hash, fingerTable, (senderHash, senderRef), active, myMovies)
        }
        else
          Behaviors.same
      case (context, FixRandomFinger()) =>
        // Check random finger table entry
        val i = scala.util.Random.nextInt(m)
        fingerTable(0)._3 ! FindPredSucc(context.self, fingerTable(i)._1, i)
        Behaviors.same
      case (context, BeginStabilizeSchedule()) =>
        // Set up stabilization scheduling
        implicit val ec = context.system.executionContext
        val scheduler = context.system.scheduler
        // context.log.info(s"Node ${key} sets up its stabilization schedule")
        // After 10 seconds, every 10 seconds verify my immediate successor with stabilize
        scheduler.scheduleWithFixedDelay(5.seconds, 10.seconds)(() =>
          fingerTable(0)._3 ! Stabilize(hash, context.self)
        )
        // After 10 seconds, every 15 seconds randomly check a finger table entry and verify it using successor
        scheduler.scheduleWithFixedDelay(10.seconds, 15.seconds)(() =>
          context.self ! FixRandomFinger()
        )
        Behaviors.same
      case (context, StopNode()) =>
        context.log.info(s"Node ${key} is being dropped")
        // Tell my successor that I'm leaving
        fingerTable(0)._3 ! RemoveMe(hash)

        // Transfer my data to predecessor
        if (pred != null) {
          movies.foreach(m => pred._2 ! AddMovie(m, null, true, 0))
          // Tell any nodes that might have me in their finger table that I'm leaving
          // Update predecessors about me
          (0 until m).foreach(j =>
            pred._2 ! UpdateIfHasRemoved(
              (hashVal(hash) - BigInt(2).pow(j))
                .mod(BigInt(2).pow(m) - 1)
                .toByteArray,
              hash
            )
          )
        }
        // If I have no predecessor, try to get my succeessor to inform my predecessors
        else {
          // Update predecessors about me
          (0 until m).foreach(j =>
            fingerTable(0)._3 ! UpdateIfHasRemoved(
              (hashVal(hash) - BigInt(2).pow(j))
                .mod(BigInt(2).pow(m) - 1)
                .toByteArray,
              hash
            )
          )
        }
        // Stop this node
        Behaviors.stopped
      case (context, RemoveMe(dropNode: Array[Byte])) =>
        // Go through my finger table and remove any instances of this node
        context.log.info(s"Node ${key} is removing ${dropNode} from its ft")
        // Make new finger table with no mentions of dropped node
        var newFT : IndexedSeq[(Array[Byte], Array[Byte], ActorRef[NodeCommand])] = IndexedSeq[(Array[Byte], Array[Byte], ActorRef[NodeCommand])]()
        fingerTable.foreach(f => {
          if (f._2 == dropNode)
            newFT = newFT :+ (f._1, null, null)
          else
            newFT = newFT :+ (f._1, f._2, f._3)
        })
        // If the dropped node is my predcessor, remove my predecessor. Try to find a new one
        var newPred = pred
        if (pred != null && pred._1 == dropNode) {
          // Send a message to myself asking to find my predecessor
          newPred = null
          context.self ! FindPredSucc(context.self, hash, -1)
        }

        // If this node's successor has been removed, the node is now inactive, and its successor must be found
        var newActive = true
        if (newFT(0)._2 == null) {
          // Node is no longer active, send message to find successor
          newActive = false
          context.self ! FindPredSucc(context.self, hash, 0)
        }
        // Set the node's behavior to new values
        node(key, hash, newFT, newPred, newActive, movies)
      case (context, UpdateIfHasRemoved(id: Array[Byte], dropNode: Array[Byte])) =>
        // Find either successor or closest preceding finger
        val findSuccAttempt: (Boolean, Array[Byte], ActorRef[NodeCommand])= tryFindSuccessor(id, hash, context.self, fingerTable)
        // Successor has been found, so I am predecessor. Tell myself to update finger table
        if (findSuccAttempt._1) {
          context.self ! RemoveMe(dropNode)
        }
        // Predecessor was not found, ask closest preceding finger
        else {
          findSuccAttempt._3 ! UpdateIfHasRemoved(id, dropNode)
        }
        Behaviors.same
    }
  def pause(
      key: String,
      hash: Array[Byte],
      fingerTable: IndexedSeq[
        (Array[Byte], Array[Byte], ActorRef[NodeCommand])
      ],
      pred: (Array[Byte], ActorRef[NodeCommand]),
      active: Boolean,
      movies: IndexedSeq[Movie]
  ): Behavior[NodeCommand] = {
    Behaviors.setup { context =>
      Behaviors.withStash(1_000_000) { buffer =>
        Behaviors.receiveMessage[NodeCommand] {
          case WriteLogs(replyTo) =>
            // context.log.info(s"Node ${key} writing logs")
            Util.yamlDump(
              yamlOfState(key, hash, fingerTable, pred, active, movies)
            )
            replyTo ! Bootstrapper.ChildStateWritten(context.self)
            Behaviors.same
          case Resume() =>
            buffer.unstashAll(
              node(key, hash, fingerTable, pred, active, movies)
            )
          case other =>
            buffer.stash(other)
            Behaviors.same
        }
      }
    }
  }
  def yamlOfState(
      key: String,
      hash: Array[Byte],
      fingerTable: IndexedSeq[
        (Array[Byte], Array[Byte], ActorRef[NodeCommand])
      ],
      pred: (Array[Byte], ActorRef[NodeCommand]),
      active: Boolean,
      movies: IndexedSeq[Movie]
  ): String = {
    try {
      s"""  - Node:
    key: ${key}
    hash: ${Util.stringOfBytes(hash)}
    finger-table-${key}: 
      ${if (fingerTable != null)
        fingerTable
          .foldLeft(new StringBuilder())((sb, entry) =>
            sb.append(
              s"\n        - entry:\n            index: ${Util
                .stringOfBytes(entry._1)}\n            value: ${Util
                .stringOfBytes(entry._2)}\n            pred: ${Util
                .stringOfBytes(pred._1)}\n            active: ${active}"
            )
          )
          .toString()
      else ""}
    movies: ${movies
        .foldLeft(new StringBuilder())((sb, movie) =>
          sb.append(
            s"\n      - movie:\n        title: ${movie.title}\n        release: ${movie.release_date}\n        revenue: ${movie.studio}\n"
          )
        )
        .toString}\n"""
    } catch {
      case _: Throwable => ""
    }
  }
}
