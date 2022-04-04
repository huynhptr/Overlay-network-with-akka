package com.overlay
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import com.overlay.Util
import com.overlay.Bootstrapper
import akka.actor.SupervisorStrategyLowPriorityImplicits
import akka.actor.typed.SupervisorStrategy
import com.overlay.Bootstrapper.JoinNode
import com.overlay.Bootstrapper.DropANode
import com.overlay.RestApi
import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer
import scala.concurrent.duration._
import java.time.format.DateTimeFormatter
import scala.util.Random

object Simulator {
  trait SimulatorCommand
  final case class GiveState() extends SimulatorCommand
  final case class WorldStop() extends SimulatorCommand
  final case class WorldResume(replyTo: ActorRef[Bootstrapper.OverlayCommand])
      extends SimulatorCommand
  final case class LogDump() extends SimulatorCommand
  final case class SetConfiguration(config: Configuration)
      extends SimulatorCommand
  final case class SuccesfullyPausedChildren() extends SimulatorCommand
  final case class Write(rmovie: Movie, startTime: Long) extends SimulatorCommand
  final case class Read(replyTo: ActorRef[SimulatorCommand], name: String,startTime:Long)
      extends SimulatorCommand
  final case class MovieResponse(movie: Option[Movie])
      extends SimulatorCommand // to be sent to client
  final case class StoreResponse(name: String, success: Boolean)
      extends SimulatorCommand //to be sent to client

  def apply(config: Option[Configuration]): Behavior[SimulatorCommand] = {
    if (config.isDefined) setup(config.get)
    else Behaviors.withStash(1_000_000) { buffer => waitForConfig(buffer) }
  }

  var pauseCount = 0
  def setup(config: Configuration): Behavior[SimulatorCommand] = {
    Behaviors.withTimers { timer =>
      Behaviors.setup { context =>
        val bootstrapper = context.spawn(Bootstrapper(), "Bootstrapper")
        
        val client = context.spawn(Client(context.self,config),"Client")
        //TODO: Go through config file and set up simulator
        val intervalForFreeze = config.timeMarkForPause.seconds
        timer.startTimerAtFixedRate(
          WorldStop(),
          intervalForFreeze
        )
        timer.startTimerAtFixedRate(GiveState(), 2.second)
        context.watch(bootstrapper)
        active(config, bootstrapper)
      }
    }
  }

  def active(
      config: Configuration,
      bootstrapper: ActorRef[Bootstrapper.OverlayCommand]
  ): Behavior[SimulatorCommand] = {
    Behaviors.setup { context =>
      Behaviors
        .supervise[SimulatorCommand] {
          Behaviors.receiveMessage[SimulatorCommand] {
            case GiveState() =>
              context.log.info("(Simulator:Active) Giving State")
              bootstrapper ! Bootstrapper.GiveState()
              Behaviors.same
            case msg @ Bootstrapper.JoinNode(name: String) =>
              context.log.info(
                s"(Simulator:Active) Joining message sent for node: ${name}"
              )
              bootstrapper ! msg
              Behaviors.same
            case WorldStop() =>
              context.log.info(
                "(Simulator:Active) World Stopping ... stashing messages and pausing network"
              )
              Util.yamlDump(yamlOfState(pauseCount))
              // After each pause cycle, check to see if any nodes will be dropped
              if (Random.nextFloat() < config.nodeFailureChance) {
                // Send a message to the bootstrapper telling it to drop a node
                bootstrapper ! DropANode()
                context.log.info("Simulator Dropping a Node ...")
              }
              pauseCount += 1
              bootstrapper ! Bootstrapper.WorldStop(context.self)

              Behaviors.withStash(1_000_000) { buffer =>
                waitForWorldResume(config, buffer,bootstrapper)
              }
            case Write(movie,startTime) =>
              bootstrapper ! Bootstrapper.UploadMovie(context.self, movie,startTime)
              Behaviors.same
            case StoreResponse(movieName, success) =>
              if (success) {
                context.log.info(s"Uploaded $movieName successfully!")
              } else {
                context.log.info(
                  s"Storing $movieName is failed. Try again later!"
                )
              }
              Behaviors.same
            case Read(replyTo, name,startTime) =>
              // context.log.info(
              //   s"${context.self.path.name} is finding movie ${name}"
              // )
              bootstrapper ! Bootstrapper.DownloadMovie(name, context.self,startTime)
              Behaviors.same
            case MovieResponse(movie) => {
              movie match {
                case Some(movie) =>
                  val mvFormat =
                    s"[NAME: ${movie.title}] [RELEASE: ${movie.release_date}] [STUDIO: ${movie.studio}]"
                  context.log.info(
                    s"${context.self.path.name} receives movie $mvFormat"
                  )
                  context.log.info(s"You got movie $mvFormat")
                case None =>
                    // Util.yamlDumpResults(s"  Read-fail: ${context.self.path.name}\n")
              }
              Behaviors.same
            }
            case _ => 
              Behaviors.same

          }
        }
        .onFailure(SupervisorStrategy.restart)
    }
  }
  def waitForConfig(
      buffer: StashBuffer[SimulatorCommand]
  ): Behavior[SimulatorCommand] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {

        case SetConfiguration(config) =>
          context.log.info("Configuration set...")
          buffer.unstashAll(setup(config))
        case other =>
          context.log.info("Wating for config file ... so buffed message")
          buffer.stash(other)
          Behaviors.same
      }
    }
  def waitForWorldResume(
      config: Configuration,
      buffer: StashBuffer[SimulatorCommand],
      bootstrapper: ActorRef[Bootstrapper.OverlayCommand]
  ): Behavior[SimulatorCommand] = {
    Behaviors.withTimers { timer =>
    Behaviors.setup { context =>
       timer.startSingleTimer(WorldResume(bootstrapper),5.second)

      Behaviors.receiveMessage {

        case WorldResume(replyTo: ActorRef[Bootstrapper.OverlayCommand]) =>
          context.log.info("World Resuming...")
          buffer.unstashAll(active(config, replyTo))
        case other =>
          context.log.info("(Simulator) Paused ...")
          //buffer.stash(other)
          Behaviors.same
      }
    }
  }
  }

  def yamlOfState(pauseCount: Int): String =
    s"Simulation-number-${pauseCount.toString}:\n"

}
