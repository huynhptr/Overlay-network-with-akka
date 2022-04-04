package com.overlay.CAN



import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import com.overlay.Util
import com.overlay.Bootstrapper
import akka.actor.SupervisorStrategyLowPriorityImplicits
import akka.actor.typed.SupervisorStrategy
import com.overlay.Bootstrapper.JoinNode
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
import java.util.concurrent._
import com.overlay.Configuration
import com.overlay.Movie

object Client {
//final case class
  trait ClientCommands

  def apply(
      replyTo: ActorRef[Simulator.SimulatorCommand],
      config: Configuration
  ): Behavior[ClientCommands] = {
    Behaviors.withTimers { timer =>
      Behaviors.setup[ClientCommands] { context =>
        implicit val ec = context.system.executionContext
        val requestsPerMinute = requestsPerMinuteOfConfig(config)
        val reads = (requestsPerMinute * config.readWriteRatio).toInt
        val writes = requestsPerMinute - reads
        Thread.sleep(1000*10)
        context.system.scheduler.scheduleWithFixedDelay(
           0.second,
          config.durationOfSimulator.second)(
           () => {
            readOrWrite(replyTo, config.listOfItems.toIndexedSeq, reads, writes)
            Util.yamlDumpResults(s"\nConfiguration:\n  Nodes: ${config.numberOfComputers}\n  Reads: $reads\n  Writes: $writes\n  Read-to-Writes: ${config.readWriteRatio} \n")
          })
        
        active(replyTo)
      }
    }
  }

  def active(
      replyTo: ActorRef[Simulator.SimulatorCommand]
  ): Behavior[ClientCommands] = {
    Behaviors.receiveMessage[ClientCommands] {
      case _ =>
        Behaviors.same
    }
  }

  def requestsPerMinuteOfConfig(config: Configuration): Int =
    new scala.util.Random()
      .between(config.minRequestsPerMinute, config.maxRequestsPerMinute)

  def readOrWrite(
      actorRef: ActorRef[Simulator.SimulatorCommand],
      items: IndexedSeq[Movie],
      reads: Int,
      writes: Int
  ): Unit =
    (reads, writes) match {
      case (reads, writes) if reads == 0 && writes == 0 => ()
      case (reads, writes) if writes == 0 => {
        actorRef ! Simulator.Read(
          actorRef,
          items(randomIndex(items.length)).title, System.currentTimeMillis()
        ); readOrWrite(actorRef, items, reads - 1, writes)
      }
      case (reads, writes) if reads == 0 => {
        actorRef ! Simulator.Write(items(randomIndex(items.length)),System.currentTimeMillis());
        readOrWrite(actorRef, items, reads, writes - 1)
      }
      case (reads, writes) => {
        if (randomOr == 0) {
          actorRef ! Simulator.Write(items(randomIndex(items.length)),System.currentTimeMillis());
          readOrWrite(actorRef, items, reads, writes - 1)
        } else {
          actorRef ! Simulator.Read(
            actorRef,
            items(randomIndex(items.length)).title, System.currentTimeMillis()
          ); readOrWrite(actorRef, items, reads - 1, writes)
        }
      }
    }

  def randomOr: Int = new scala.util.Random().between(0, 2)
  def measureTime(f: => Unit,reads:Int, writes:Int, ratio:Double) = {
    val start = System.currentTimeMillis()
    f
    val end = System.currentTimeMillis()

    Util.yamlDumpResults(s"Configuration:\n  Reads: $reads\n  Writes: $writes\n  Read-to-Writes: $ratio\n ")
  }
  def randomIndex(len: Int): Int =
    new scala.util.Random().between(0, len)
}
