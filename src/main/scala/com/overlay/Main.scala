package com.overlay

import akka.actor.Props
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import akka.actor.typed.SupervisorStrategy
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef, EntityTypeKey}
import com.overlay.Bootstrapper.JoinNode
import com.overlay.Marshallers.configurationFormat
import spray.json.JsonParser

import scala.io.Source._
import scala.concurrent.duration._
import com.overlay.CAN.Simulator

import scala.concurrent.ExecutionContextExecutor


object Main extends App {
    val fileConfiguration = ConfigFactory.load("config_input_one")
    val isChord = fileConfiguration.getBoolean("input.isChord")
    val withRestApi = fileConfiguration.getBoolean("input.withRestApi")
  if(withRestApi) {
    //For "Production", sets up api endpoint with no config, waits for POST message at configuration endpoint to set up actor system
    if(isChord){

     val bindingFuture = RestApi(com.overlay.Simulator(None), None)
    }
    else{
      val bindingFuture = com.overlay.CAN.RestApi(com.overlay.CAN.Simulator(None),None)
    }
  }
  else{
    //For testing with the default configuration (without having restApi up)
      val config = JsonParser(fromFile("src/main/resources/sample_input.json").getLines.mkString)
                      .convertTo[Configuration]

      if (!config.isChord) { //use CAN
        implicit var system = ActorSystem(com.overlay.CAN.Simulator(Some(config)), "OverlayNetwork")

        implicit val executionContext = system.executionContext

        val movies = JsonParser(fromFile("src/main/resources/sample_input.json").getLines.mkString)
          .convertTo[Configuration].listOfItems

          val sharding = ClusterSharding(system)

          val TypeKey = EntityTypeKey[com.overlay.CAN.Simulator.SimulatorCommand]("Simulator")

          sharding.init(Entity(TypeKey)(createBehavior = entityContext => Simulator.apply(Some(config))))
          val simulator: EntityRef[com.overlay.CAN.Simulator.SimulatorCommand] = sharding.entityRefFor(TypeKey, "simulator")

          println("------------------------------------------------------")
          simulator ! CAN.Bootstrapper.JoinNode("Node-")
          simulator ! com.overlay.CAN.Simulator.GiveState()
          Thread.sleep(1500)
          simulator ! CAN.Bootstrapper.JoinNode(1.toString)
          Thread.sleep(1500)
          simulator ! CAN.Bootstrapper.JoinNode(2.toString)
          Thread.sleep(1500)
          simulator ! CAN.Bootstrapper.JoinNode(3.toString)
          Thread.sleep(1500)
          simulator ! CAN.Bootstrapper.JoinNode(4.toString)
          Thread.sleep(1500)

          movies foreach(movie => {
            simulator ! CAN.Simulator.Write(movie,System.currentTimeMillis())
            Thread.sleep(100)}
            )

      } else { //use Chord

        implicit var system = ActorSystem(com.overlay.Simulator(Some(config)), "OverlayNetwork")
        implicit val executionContext = system.executionContext
        val movies = JsonParser(
          fromFile("src/main/resources/sample_input.json").getLines.mkString
        )
          .convertTo[Configuration]
          .listOfItems

        val sharding = ClusterSharding(system)

        val TypeKey = EntityTypeKey[com.overlay.Simulator.SimulatorCommand]("Simulator")

        sharding.init(Entity(TypeKey)(createBehavior = entityContext => com.overlay.Simulator.apply(Some(config))))
        val simulator: EntityRef[com.overlay.Simulator.SimulatorCommand] = sharding.entityRefFor(TypeKey, "simulator")

        println("------------------------------------------------------")
        simulator ! com.overlay.Bootstrapper.JoinNode("Node-")
        simulator ! com.overlay.Simulator.GiveState()
        Thread.sleep(1500)
        simulator ! com.overlay.Bootstrapper.JoinNode(1.toString)
        Thread.sleep(1500)
        simulator ! com.overlay.Bootstrapper.JoinNode(2.toString)
        Thread.sleep(1500)
        simulator ! com.overlay.Bootstrapper.JoinNode(3.toString)
        Thread.sleep(1500)
        simulator ! com.overlay.Bootstrapper.JoinNode(4.toString)
        Thread.sleep(1500)

        movies foreach(movie => {
          simulator ! com.overlay.Simulator.Write(movie,System.currentTimeMillis())
          Thread.sleep(100)}
          )
      }
  }
}
