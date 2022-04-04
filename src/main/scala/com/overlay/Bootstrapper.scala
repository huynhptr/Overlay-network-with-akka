package com.overlay
import akka.actor.Status.Success
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
import com.overlay.Simulator.SimulatorCommand
import akka.actor.typed.scaladsl.StashBuffer
import com.overlay.Node.NodeCommand
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Random

object Bootstrapper{

  trait OverlayCommand extends SimulatorCommand
  final case class NodeTerminated(hash: Array[Byte], key: String)
      extends OverlayCommand
  final case class JoinNode(name: String) extends OverlayCommand
  final case class WorldStop(replyTo: ActorRef[Simulator.SimulatorCommand])
      extends OverlayCommand
  final case class WorldResume(replyTo: Simulator.SimulatorCommand)
      extends OverlayCommand
  final case class StopNode(ref: ActorRef[Node.NodeCommand])
      extends OverlayCommand
  final case class GiveState() extends OverlayCommand
  final case class UploadMovie(replyTo: ActorRef[SimulatorCommand], movie: Movie,startime:Long) extends OverlayCommand
  final case class DownloadMovie(name: String, replyTo: ActorRef[Simulator.SimulatorCommand],startTime:Long) extends OverlayCommand
  final case class ChildPause(replyTo: ActorRef[Node.NodeCommand])
      extends OverlayCommand
  final case class ChildStateWritten(replyTo: ActorRef[Node.NodeCommand])
      extends OverlayCommand
  final case class MaxDurationForChildPause()
      extends OverlayCommand
  final case class DropANode() extends OverlayCommand

  def apply(): Behavior[OverlayCommand] =
    bootstrapper(Array.empty)

  def bootstrapper(
      nodes: Array[ActorRef[Node.NodeCommand]]
  ): Behavior[OverlayCommand] =
    Behaviors.withStash(100_000_00) { buffer =>
      Behaviors
        .supervise[OverlayCommand] {
          Behaviors.receive[OverlayCommand] { (context, message) =>
            message match {
              case NodeTerminated(hash, key) =>
                context.log.info(
                  s"Node terminated [Key,Hash]:[${key}, ${Util.stringOfBytes(hash)}]"
                )
                Behaviors.same
              case JoinNode(name: String) =>
                context.log.info(s"Joining node: ${name}")

                //create key and hash for the new node
                val key = name
                val hash = Util.md5(key)

              //spawn new node using hash as node's name
              val node = context.spawn(Node(key, hash, null, null, false, IndexedSeq.empty), Util.stringOfBytes(hash))
              context.log.info(s"KEY: ${key}, HASH: ${hash}")
              //watch for node termination
              context.watchWith(node, NodeTerminated(hash, key))

              nodes.length match {
                case 0 =>
                  context.log.info("First node joined!") //do nothing if this is the first node
                  node ! Node.ExistingNodeRef(null)

                //send reference of an existing node to the new node
                case _ =>
                  var foundNode:Boolean = false
                  val randomGenerator = scala.util.Random
                  val existingNode = nodes(
                    randomGenerator.nextInt(nodes.length)
                  ) //take a random node
                  context.log.info(
                    s"Sending reference of node ${existingNode.path.name} to node ${key}"
                  )
                  node ! Node.ExistingNodeRef(existingNode)
                }
              bootstrapper(nodes :+ node) //Recursive call to reflect functional style
            case UploadMovie(replyTo, movie,startTime) =>
              val randomGenerator = scala.util.Random
              val existingNode = nodes(randomGenerator.nextInt(nodes.length)) //take a random node
              existingNode ! Node.AddMovie(movie, replyTo, false,startTime)
              Behaviors.same
            case DownloadMovie(name, replyTo,startTime) =>
              val randomGenerator = scala.util.Random
              val existingNode = nodes(randomGenerator.nextInt(nodes.length)) //take a random node
              existingNode ! Node.GetMovie(name, replyTo, false,startTime)
              Behaviors.same
            case StopNode(actor) =>
              context.log.info("Deleting node")
              context.stop(actor)
              Behaviors.same
            case GiveState() =>
              context.log.info(
                "(Bootstrapper) Initiating state-wide logging."
              )
              // nodes.foreach(_ ! Node.GiveState())
              Behaviors.same
            case WorldStop(replyTo: ActorRef[Simulator.SimulatorCommand]) =>
              context.log.info("(BootStrapper) World Stopping...")
              nodes foreach (_ ! Node.Pause(context.self))
              if(nodes.isEmpty){replyTo ! Simulator.WorldResume(context.self)
              Behaviors.same
              }
              else
                waitForChildrenPausing(nodes, replyTo, buffer, Set.empty)
            case MaxDurationForChildPause() | ChildPause(_) | ChildStateWritten(_) =>
                Behaviors.same
            case DropANode() =>
              val dropIndex = Random.nextInt(nodes.length)
              val dropRef = nodes(dropIndex)
              dropRef ! Node.StopNode()
              bootstrapper(nodes.patch(dropIndex, Nil, 1))
            }
          }
        }
        .onFailure(
          SupervisorStrategy.restart.withStopChildren(false)
        ) // Same children when bootstrapper node restarts
    }
  def waitForChildrenPausing(
      nodes: Array[ActorRef[Node.NodeCommand]],
      replyTo: ActorRef[Simulator.SimulatorCommand],
      buffer: StashBuffer[OverlayCommand],
      paused: Set[ActorRef[Node.NodeCommand]]
  ): Behavior[OverlayCommand] =
  Behaviors.withTimers { timer => 
    Behaviors.setup { context =>
      timer.startSingleTimer(MaxDurationForChildPause(),5.seconds)
      Behaviors.receiveMessage {
        case WorldResume(replyTo: ActorRef[Simulator.SimulatorCommand]) =>
          context.log.info("World Resuming...")
          buffer.unstashAll(bootstrapper(nodes))
        
        case MaxDurationForChildPause() =>
          context.log.info("(Bootstrapper) Maximum duration hit, telling nodes to write logs and resume")
          nodes foreach (_ ! Node.WriteLogs(context.self))
          waitForChildrenPausing(nodes,replyTo,buffer,Set.empty)

        case ChildPause(child: ActorRef[Node.NodeCommand]) =>
          context.log.info(s"(Bootstrapper) Nodes: ${nodes.length}")
          val newlyPaused = paused + child
          context.log.info(s"${newlyPaused.toArray.length}vs ${nodes.length} ")
            if(Range(nodes.length - (nodes.length/2), nodes.length+1) contains newlyPaused.toArray.length){
            nodes foreach (_ ! Node.WriteLogs(context.self))
             context.log.info(
              "(Bootstrapper) Telling all nodes to write logs, all children received pause message."
            )
            // after all the children nodes have been paused, make them write to yaml file
            // then wait for ChildStateWritten messages to be sent by all nodes to unpause bootstrapper
            waitForChildrenPausing(nodes,replyTo,buffer,Set.empty)
          } else {
            context.log.info(
              "(Bootstrapper) ChildPause communicated to Bootstrapper."
            )
            waitForChildrenPausing(nodes, replyTo, buffer, newlyPaused)
          }

        case ChildStateWritten(child: ActorRef[NodeCommand]) =>
          val newlyPaused = paused + child
          if (Range(nodes.length - (nodes.length/2), nodes.length+1) contains newlyPaused.toArray.length) {
    
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
        case DropANode() =>
          val dropIndex = Random.nextInt(nodes.length)
          val dropRef = nodes(dropIndex)
          dropRef ! Node.StopNode()
          context.log.info(s"Dropping random node ${nodes(dropIndex)}")
          bootstrapper(nodes.patch(dropIndex, Nil, 1))
        case other =>
          context.log.info("(Bootstrapper) Paused ...")
          buffer.stash(other)
          Behaviors.same
      }
    }
    }
}
