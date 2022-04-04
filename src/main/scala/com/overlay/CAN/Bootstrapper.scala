package com.overlay.CAN

import akka.actor.Status.Success
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.Terminated
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import akka.pattern.ask
import com.overlay.CAN.Simulator._
import com.overlay.CAN._
import com.overlay.Movie
import akka.actor.typed.scaladsl.StashBuffer
import com.overlay.CAN.Node.NodeCommand
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import akka.util.Index
import com.overlay.CAN
import scala.collection.mutable.ArrayBuffer
import com.overlay.CAN.Node.GiveInfoReply
import scala.collection.immutable.NumericRange

object Bootstrapper {
  trait Record 
  final case class NodeRecord(name:String,zones: ArrayBuffer[Zone],active:Boolean)
 
  def overlap(a:Zone,b:Zone) ={
      // println(s"(Overlap) predicate a: $a")
      // println(s"(Overlap) predicate b: $b")
      val count = a.intervals.zip(b.intervals).map( (l) => {
            //  println(s"First boool: ${Util.inRange(l._2.min,  l._1.min, l._1.max,true,true)}");
            //   println(s"Second boool: ${Util.inRange(l._2.max,l._1.min,l._1.max,true,true) }");
             Util.inRange(l._2.min,  l._1.min, l._1.max,true,true) ||  Util.inRange(l._2.max,l._1.min,l._1.max,true,true)}).count(_ == true) 
      
      // println("(Overlap) dimensions fulfilling predicate: " + count)
      count == Simulator.dimensions-1
  }
  def shareBorder(a:Zone, b:Zone) ={
    // println(s"a-intervals: ${Util.stringOfIntervals(a.intervals)} ")
    // println(s"b-intervals: ${Util.stringOfIntervals(b.intervals)} ")

    val ct = a.intervals.zip(b.intervals).map( (l) => { val a = (Util.inRange((l._2.min -1), l._1.min, l._1.max,true,true)); val b = Util.inRange(l._2.max+1,l._1.min,l._1.max,true,true); val c = l._1 == l._2; a || b || c || l._1.min == l._2.max || l._1.max == l._2.min || l._1.max == l._2.max || l._1.min == l._2.min} ).count(_ == true)

    // println(s"(ShareBorder) Dimensions that share border: $ct")
    ct == Simulator.dimensions
  }

  def updateNeighbors(z: Zone, allZones: Seq[Zone])= {
    // check if d-1 dimensions are overlapping
    //val overalapped = allZones.filter(overlap(z,_))
    val overlapping = allZones.filter(overlap(z,_))
    // println(s"Nodes: ${allZones.length}")
    // println("-----overlapping-----")
    // println(overlapping.length)
    // println("-----overlapping-----")
    val neighborZones = overlapping.filter(shareBorder(z,_))
    // println("------neighborzones-------")
    // println(z.toString)
    // println(neighborZones.length)
    // println("------neighborzones-------")

    val updatedRoutingTable =  neighborZones.map(z => Neighbor(z.intervals,z.ref.get)).map[Option[Neighbor]](Some(_)).toArray
    // println("---routing----")
    // updatedRoutingTable.foreach(r => println(s"printng routing table neighbor: ${if(r.isDefined) r.get.toString else "None"})"))
    // println(updatedRoutingTable.length)
    // println("---routing----")
    Zone(z.intervals,z.lastSplit,z.movies,updatedRoutingTable,z.ref)
  }
  def establishNeighbors(record:NodeRecord, network: Seq[NodeRecord] ):Unit= {
    // println(s"Network count: ${network.length}")
    // if(network.length == 0) { println("No other zones"); return ()}
    val allZones = network.flatMap(z => z.zones )
    val updatedZones =record.zones.map(updateNeighbors(_,allZones)).filter(_.ref.isDefined)
    // println("-----------")
    println(updatedZones.length)
    // println("------------")
    updatedZones.head.ref.get ! Node.UpdateState(updatedZones)
  }
  type nodeParams = (String,ArrayBuffer[Zone],Boolean)
 trait OverlayCommand extends SimulatorCommand
  final case class NodeTerminated(name:String, interval: Array[Interval])
      extends OverlayCommand
  final case class JoinNode(name: String) extends OverlayCommand
  final case class AddNode(name:String, node: ArrayBuffer[Zone],active:Boolean) extends OverlayCommand
  final case class WorldStop(replyTo: ActorRef[Simulator.SimulatorCommand])
      extends OverlayCommand
  final case class WorldResume(replyTo: Simulator.SimulatorCommand)
      extends OverlayCommand
  final case class StopNode(ref: ActorRef[Node.NodeCommand])
      extends OverlayCommand
  final case class GiveState() extends OverlayCommand
  final case class UploadMovie(
      replyTo: ActorRef[SimulatorCommand],
      movie: Movie,
      startime: Long
  ) extends OverlayCommand
  final case class DownloadMovie(
      name: String,
      replyTo: ActorRef[Simulator.SimulatorCommand],
      startTime: Long
  ) extends OverlayCommand
  final case class ChildPause(replyTo: ActorRef[Node.NodeCommand])
      extends OverlayCommand
  final case class ChildStateWritten(replyTo: ActorRef[Node.NodeCommand])
      extends OverlayCommand
  final case class MaxDurationForChildPause() extends OverlayCommand
  final case class ListingResponse(listing: Receptionist.Listing)
      extends OverlayCommand
  
  final case class GiveInfoAdapter(name:String,zones:ArrayBuffer[Zone],active:Boolean,refOfNode:ActorRef[NodeCommand]) extends OverlayCommand
  def apply(): Behavior[OverlayCommand] =
    bootstrapper(Array.empty)

  
  def bootstrapper(
      nodes: Array[ActorRef[com.overlay.CAN.Node.NodeCommand]]
  ): Behavior[OverlayCommand] =
    Behaviors.setup[OverlayCommand] { context =>
      val listingResponeAdapter =
        context.messageAdapter[Receptionist.Listing](ListingResponse)


      Behaviors.withStash(100_000_00) { buffer =>
        Behaviors
          .supervise[OverlayCommand] {
            Behaviors.receiveMessage {
              case ListingResponse(Node.nodeServiceKey.Listing(listings)) =>
                // context.log.info("New listing set on update!")
                // listings foreach (a => println(s"(From listing) Node: ${a.toString()}\n"))
                // context.log.info("--------------------")
                implicit val timeout:Timeout = 15.seconds
                implicit val system = context.system
                implicit val ec = context.executionContext
                //blocking thread to ping all nodes for their state info
                try{
                val responses: Seq[NodeCommand] = Await.result(Future.sequence(listings.map(_.ask(Node.GiveInfo(_))).toSeq), timeout.duration)
                val nodes = responses.map(b =>  b match {
                  case GiveInfoReply(n,z,a,_) => Some(NodeRecord(n,z,a))
                  case _ => None
                }
                 ).filter(_.isDefined).map(_.get)
                // context.log.info(s"node amounts: ${nodes.length}")
               nodes.foreach( n => establishNeighbors( n, nodes.filter( n != _)))
                } catch {
      case _: Throwable => ""
                   }
                

               bootstrapper(listings.toArray)
              case AddNode(name,node,active) =>
                val n  = context.spawn(com.overlay.CAN.Node(name,node,active), name)
                Behaviors.same

              case NodeTerminated(name, interval) =>
                context.log.info(
                  s"Node terminated [Key]:[${name.toString()}\n ${Util.stringOfIntervals(interval)}]"
                )
                Behaviors.same
              case JoinNode(name: String) =>
                context.log.info(s"Joining node: ${name}")
                nodes.length match {
                  case 0 =>
                    context.log.info(
                      "First node joined!"
                    )
                    // one dimension has a maximum bit length of the total hashlenght bits, 128, divided by the amount of coordinates (dimensions) we are having.
                    val max: Long = Util.hashLength / Simulator.dimensions
                    val defaultInterval =
                      Util.integerRangeOfBits(
                        max
                      ) // for example, for 8, -128 to 127 is returned as the interval
                    context.log.info(s"max: $max")
                    // starting node takes the entire zone, so set each dimension to take max of each dimension
                    val defaultIntervals = Array.fill[Interval](dimensions)(Interval(0, 0))
                     (0 until dimensions) foreach((defaultIntervals(_) = defaultInterval))
                    val firstZone = Zone(defaultIntervals,-1,IndexedSeq(Movie("Many Adventures of Winnie the Pooh (1977)","1977-03-10 16:00:00-08:00","Walt Disney Productions")),Array.empty,None)

                    val node = context.spawn(
                      Node(
                        name,
                        ArrayBuffer.fill[Zone](1){firstZone},
                        true,
                      ),
                      name
                    )
                    context.watchWith(node, NodeTerminated(name,defaultIntervals))
                    bootstrapper(nodes :+ node)
                  case _ =>
                    val key = name
                    val hash = Util.md5(key)
                    val p = Util.pointOfHash(hash)
                    var foundNode: Boolean = false
                    val randomGenerator = scala.util.Random
                    val existingNode = nodes(
                      randomGenerator.nextInt(nodes.length)
                    ) //take a random node
                    context.log.info(
                      s"Sending reference of node ${existingNode.path.name} to node ${key}"
                    )
                    existingNode ! Node.RouteAction(p,Node.AddNode(),context.self)
                    Behaviors.same
                }
              case UploadMovie(replyTo, movie, startTime) =>
                val randomGenerator = scala.util.Random
                val existingNode =
                  nodes(
                    randomGenerator.nextInt(nodes.length)
                  ) //take a random node

                val point = Util.pointOfHash(Util.md5(movie.title))
                existingNode ! Node.RouteAction(point,Node.AddMovie(movie,replyTo, false, startTime),context.self)
                Behaviors.same
              case DownloadMovie(name, replyTo, startTime) =>
              context.log.info("(Bootstrapper) DownloadingMovie")
                val randomGenerator = scala.util.Random
                val existingNode =
                  nodes(
                    randomGenerator.nextInt(nodes.length)
                  ) //take a random node
                val point = Util.pointOfHash(Util.md5(name))

                existingNode ! Node.RouteAction(point,Node.GetMovie(name,replyTo,false,startTime),context.self)
                Behaviors.same
              case StopNode(actor) =>
                context.log.info("Deleting node")
                context.stop(actor)
                Behaviors.same
              case GiveState() =>
                context.system.receptionist ! Receptionist.Find(Node.nodeServiceKey,listingResponeAdapter)
                context.log.info(
                  "(Bootstrapper) Initiating state-wide logging."
                )
                // nodes.foreach(_ ! Node.GiveState())
                Behaviors.same
              case WorldStop(replyTo: ActorRef[Simulator.SimulatorCommand]) =>
                context.log.info("(BootStrapper) World Stopping...")
                nodes foreach (_ ! Node.Pause(context.self))
                if (nodes.isEmpty) {
                  replyTo ! Simulator.WorldResume(context.self)
                  Behaviors.same
                } else
                  waitForChildrenPausing(nodes, replyTo, buffer, Set.empty)
              case MaxDurationForChildPause() | ChildPause(_) |
                  ChildStateWritten(_) =>
                Behaviors.same
            }
          }
          .onFailure(
            SupervisorStrategy.restart.withStopChildren(false)
          ) // Same children when bootstrapper node restarts
      }
    }
  def waitForChildrenPausing(
      nodes: Array[ActorRef[Node.NodeCommand]],
      replyTo: ActorRef[Simulator.SimulatorCommand],
      buffer: StashBuffer[OverlayCommand],
      paused: Set[ActorRef[Node.NodeCommand]]
  ): Behavior[OverlayCommand] =
    Behaviors.withTimers { timer =>
      Behaviors.setup { context =>
        timer.startSingleTimer(MaxDurationForChildPause(), 5.seconds)
        Behaviors.receiveMessage {
          case WorldResume(replyTo: ActorRef[Simulator.SimulatorCommand]) =>
            context.log.info("World Resuming...")
            buffer.unstashAll(bootstrapper(nodes))

          case MaxDurationForChildPause() =>
            context.log.info(
              "(Bootstrapper) Maximum duration hit, telling nodes to write logs and resume"
            )
            nodes foreach (_ ! Node.WriteLogs(context.self))
            waitForChildrenPausing(nodes, replyTo, buffer, Set.empty)

          case ChildPause(child: ActorRef[Node.NodeCommand]) =>
            context.log.info(s"(Bootstrapper) Nodes: ${nodes.length}")
            val newlyPaused = paused + child
            context.log.info(
              s"${newlyPaused.toArray.length}vs ${nodes.length} "
            )
            if (
              Range(
                nodes.length - (nodes.length / 2),
                nodes.length + 1
              ) contains newlyPaused.toArray.length
            ) {
              nodes foreach (_ ! Node.WriteLogs(context.self))
              context.log.info(
                "(Bootstrapper) Telling all nodes to write logs, all children received pause message."
              )
              // after all the children nodes have been paused, make them write to yaml file
              // then wait for ChildStateWritten messages to be sent by all nodes to unpause bootstrapper
              waitForChildrenPausing(nodes, replyTo, buffer, Set.empty)
            } else {
              context.log.info(
                "(Bootstrapper) ChildPause communicated to Bootstrapper."
              )
              waitForChildrenPausing(nodes, replyTo, buffer, newlyPaused)
            }

          case ChildStateWritten(child: ActorRef[NodeCommand]) =>
            val newlyPaused = paused + child
            if (
              Range(
                nodes.length - (nodes.length / 2),
                nodes.length + 1
              ) contains newlyPaused.toArray.length
            ) {

              context.log.info("(Bootsrapper) Resuming all nodes")
              nodes foreach (_ ! Node.Resume())
              replyTo ! Simulator.WorldResume(context.self)
              buffer.unstashAll(Bootstrapper.bootstrapper(nodes))

            } else {
              context.log.info(
                "(Bootstrapper) Node pause communicated to Bootstrapper."
              )
              waitForChildrenPausing(nodes, replyTo, buffer, newlyPaused)
            }
          case other =>
            context.log.info("(Bootstrapper) Paused ...")
            buffer.stash(other)
            Behaviors.same
        }
      }
    }
}
