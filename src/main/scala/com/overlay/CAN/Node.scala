package com.overlay.CAN

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import com.overlay.CAN.Simulator.SimulatorCommand
import com.overlay.CAN.Bootstrapper.OverlayCommand
import com.overlay.CAN.Simulator.SimulatorCommand
import com.overlay.CAN.Util._
import scala.concurrent.duration.DurationInt
import scala.util.control.Breaks
import scala.util.control.Breaks.break
import akka.util.Index
import com.overlay.Movie
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.receptionist.Receptionist
import akka.actor.Identify
import com.overlay.Node.NodeCommand
import scala.collection.mutable.ArrayBuffer

object Node {
  trait NodeCommand extends SimulatorCommand
  final case class Say(word: String) extends NodeCommand
  final case class GiveState() extends NodeCommand
  // This message checks if the node is active
  final case class ActiveCheck(replyTo: ActorRef[Boolean]) extends NodeCommand
  final case class Stabilize(
      senderHash: Array[Byte],
      senderRef: ActorRef[NodeCommand]
  ) extends NodeCommand
  final case class BeginStabilizeSchedule() extends NodeCommand
  final case class Pause(replyTo: ActorRef[Bootstrapper.OverlayCommand])
      extends NodeCommand
  final case class WriteLogs(replyTo: ActorRef[Bootstrapper.OverlayCommand])
      extends NodeCommand
  final case class Resume() extends NodeCommand
  final case class RouteAction(
      p: Array[Long],
      action: RoutingActions,
      replyTo: ActorRef[Bootstrapper.OverlayCommand]
  ) extends NodeCommand
  final case class GiveInfo(replyTo: ActorRef[NodeCommand]) extends NodeCommand
  final case class GiveInfoReply(
      name: String,
      zones: ArrayBuffer[Zone],
      active: Boolean,
      refOfNode: ActorRef[NodeCommand]
  ) extends NodeCommand
  // after command RouteNode, what action to take?
  final case class UpdateState(zones: ArrayBuffer[Zone]) extends NodeCommand

  trait RoutingActions
  final case class AddMovie(
      movie: Movie,
      replyTo: ActorRef[SimulatorCommand],
      forceReceive: Boolean,
      startTime: Long
  ) extends RoutingActions
  final case class GetMovie(
      name: String,
      replyTo: ActorRef[Simulator.MovieResponse],
      mustHave: Boolean,
      startTime: Long
  ) extends RoutingActions
  final case class AddNode() extends RoutingActions

  final val nodeServiceKey =
    ServiceKey[NodeCommand]("nodeService") // for receptionst
  // Node variables (key, hash, finger, , active)
  // Finger table entries (start, node, ref)

  def apply(
      id: String,
      zones: ArrayBuffer[Zone],
      active: Boolean
  ): Behavior[NodeCommand] = {
    setup(id, zones, active)
  }

  def setup(id: String, zones: ArrayBuffer[Zone], active: Boolean) = {
    Behaviors.setup[NodeCommand] { context =>
      val updated = zones.map(z =>
        Zone(
          z.intervals,
          z.lastSplit,
          z.movies,
          z.routingTable,
          Some(context.self)
        )
      )
      node(id, updated, active)
    }
  }
  def node(
      id: String,
      zones: ArrayBuffer[Zone],
      active: Boolean
  ): Behavior[NodeCommand] =
    Behaviors.setup[NodeCommand] { context =>
      context.system.receptionist ! Receptionist.Register(
        nodeServiceKey,
        context.self
      )

      Behaviors.receiveMessage[NodeCommand] {
        case RouteAction(p, action, replyTo) =>
          var zoneIndex = 0
          var inZone = false;
          var i = 0;
          var timedOut =  action match {
              case GetMovie(_, _, _, t) =>(System.currentTimeMillis()- t> 1000*100)
              case AddMovie(movie, replyTo, forceReceive, startTime) =>
                 ( (System.currentTimeMillis() - startTime)> 1000*100)
              case AddNode() => false
            }
 
          // context.log.info(bo.toString())
          if ( timedOut
         ) {
            context.log.info(s"Point: (${p(0)}, ${p(1)}) has timed out!")
            Behaviors.same
          } else {
            // go through this node's zones.
            for (z <- zones) {
              if (Util.inInterval(p, z.intervals)) {
                inZone = true
                zoneIndex = i
              }
              i += 1
            }
            if (!inZone) {

              // find next zone to route by finding shortest euclidean distance
              val nextZoneIndex = scala.util.Random.between(0, zones.length)
              val nextActor = zones(nextZoneIndex).routingTable
                .filter(_.isDefined)
                .map(n => (midpointOfIntervals(n.get.intervals), n.get.ref))
                .map(t => (euclideanDistance(t._1, p), t._2))
                .sortBy(_._1)
                .headOption
              if (nextActor.isDefined)
                nextActor.get._2 ! Node.RouteAction(p, action, replyTo)
              node(id, zones, active)
            } else {
              val matchedZone = zones(zoneIndex)
              // if in the zone, match on the action
              action match {
                case GetMovie(name, replyTo, mustHave, startTime) =>
                  context.log.info(s"len: ${p.length.toString()}Trying point ${p
                    .foldLeft("")((s, l) => s + l.toString() + ", ")} ")
                  context.log.info(
                    s"Point matched to Node:$id with duration ${System.currentTimeMillis - startTime}"
                  )
                  val found =
                    matchedZone.movies.find(movie => movie.title == name)
                  replyTo ! Simulator.MovieResponse(found)
                  Behaviors.same

                case AddNode() => {
                  // found zone to split
                  println(s"Dimension Split of Node [$id]")
                  // which dimension to split on
                  val dToSplit = Util.dimensionToSplit(
                    matchedZone.lastSplit,
                    Simulator.dimensions
                  )
                  // l is the lesser interval, r is the greater
                  val (l, r) = splitZone(
                    matchedZone.intervals,
                    matchedZone.lastSplit,
                    Simulator.dimensions
                  )
                  println(s"Left interval: ${l}")
                  println(s"right interval: ${r}")

                  // make new zone interval with greater, always
                  val newZoneInterval = matchedZone.intervals.clone()
                  newZoneInterval(dToSplit) = r
                  // update zone interval to be lesser, always
                  matchedZone.intervals(dToSplit) = l

                  // transfer movies which have their hash in newzone
                  val transferredMovies: IndexedSeq[Movie] = matchedZone.movies
                    .filter(m =>
                      Util.inInterval(
                        Util.pointOfHash(Util.md5(m.title)),
                        newZoneInterval
                      )
                    )
                    .foldLeft(List[Movie]())((lst, m) => m :: lst)
                    .toIndexedSeq

                  //populate new zone with update interval and movies (from above)
                  // See: neighbors are updated by Bootstraper when the Receptionist for Nodes
                  // sends a message that this new Node (Zone) joined the network
                  val newZone = Zone(
                    newZoneInterval,
                    dToSplit,
                    transferredMovies,
                    Array.fill(1) {
                      Some(Neighbor(matchedZone.intervals, context.self))
                    },
                    None
                  )

                  // update originally matched zone with new interval and updated movie list
                  val newMoviesForOriginalZone =
                    matchedZone.movies.diff(transferredMovies)
                  val updateMatchedZone = Zone(
                    matchedZone.intervals,
                    dToSplit,
                    newMoviesForOriginalZone,
                    matchedZone.routingTable,
                    Some(context.self)
                  )
                  zones(zoneIndex) = updateMatchedZone
                  // tell bootstrapper to spawn a node with the zone constructed under *newZone*
                  replyTo ! Bootstrapper.AddNode(
                    id + (scala.math.random() * 10000).toLong,
                    ArrayBuffer.fill(1) { newZone },
                    false
                  )

                  node(id, zones, true)
                }

                case AddMovie(movie, replyTo, forceReceive, startTime) =>
                //adds movie to this matched node
                  val updatedZone = Zone(
                    matchedZone.intervals,
                    matchedZone.lastSplit,
                    matchedZone.movies.appended(movie),
                    matchedZone.routingTable,
                    matchedZone.ref
                  )
                  context.log.info(
                    s"Added movie[$movie] to Node:$id with duration ${System.currentTimeMillis - startTime}"
                  )
                  zones(zoneIndex) = updatedZone
                  node(id, zones, true)
              }
            }
          }
        case UpdateState(zones) =>
          context.log.info(s"Updated Node $id")
          node(id, zones, active)
        case GiveInfo(replyTo) =>
          replyTo ! GiveInfoReply(id, zones, active, context.self)
          Behaviors.same
        case GiveState() =>
          yamlDump(yamlOfState(id, zones, active))
          Behaviors.same
        case ActiveCheck(replyTo) =>
          replyTo ! active
          Behaviors.same
        case Pause(replyTo) =>
          context.log.info("Node pausing...")
          replyTo ! Bootstrapper.ChildPause(context.self)
          pause(id, zones, active)
      }
    }
  def pause(
      id: String,
      zones: ArrayBuffer[Zone],
      active: Boolean
  ): Behavior[NodeCommand] = {
    Behaviors.setup { context =>
      Behaviors.withStash(1_000_000) { buffer =>
        Behaviors.receiveMessage[NodeCommand] {
          case WriteLogs(replyTo) =>
            // context.log.info(s"Node ${key} writing logs")
            if (zones.length > 0)
              yamlDump(
                yamlOfState(id, zones, active)
              )
            else ()
            replyTo ! Bootstrapper.ChildStateWritten(context.self)
            Behaviors.same
          case Resume() =>
            buffer.unstashAll(
              node(id, zones, active)
            )
          case other =>
            buffer.stash(other)
            Behaviors.same
        }
      }
    }
  }
  def stringOfInterval(interval: Interval): String =
    s"(${interval.min}, ${interval.max})"

  def stringOfZones(zones: ArrayBuffer[Zone]): String =
    zones
      .foldLeft(new StringBuilder())((sb, entry) =>
        sb.append(
          s"\n     Interval-For-This-Node's-Zone: ${stringOfIntervals(entry.intervals, 6)}\n      Routing-Table:${entry.routingTable
            .filter(_.isDefined)
            .map(_.get)
            .foldLeft(
              new StringBuilder(s" Count(${entry.routingTable.filter(_.isDefined).length})\n        Neighbors:")
            )((sb, n) => sb.append(s"\n           Intervals: ${stringOfIntervals(n.intervals)}"))
            .toString} "
        )
      )
      .toString()

  def yamlOfState(
      id: String,
      zones: ArrayBuffer[Zone],
      active: Boolean
  ): String = {
    try {
      s"""-Node: $id
    Zones: ${stringOfZones(zones)}
    Zone-Count: ${zones.length}\n
    """
    } catch {
      case _: Throwable => ""
    }

  }
}
