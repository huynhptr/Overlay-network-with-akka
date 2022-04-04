package com.overlay

import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

import scala.io.StdIn
import scala.concurrent.Future
import akka.http.scaladsl
import com.overlay.Simulator
import spray.json._

import scala.concurrent.{Await, Future}
import akka.util.Timeout
import com.overlay.Marshallers._
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef, EntityTypeKey}
import com.overlay.Simulator
import com.overlay.Bootstrapper._

import scala.concurrent.duration._
object RestApi {

  def apply(
      guardian: Behavior[Simulator.SimulatorCommand],
      configuration: Option[Configuration]
  ): Future[Http.ServerBinding] = {


    implicit var system = ActorSystem(guardian, "OverlayNetwork")
    implicit val executionContext = system.executionContext

    val sharding = ClusterSharding(system)

    val TypeKey = EntityTypeKey[com.overlay.Simulator.SimulatorCommand]("Simulator")

    sharding.init(Entity(TypeKey)(createBehavior = entityContext => com.overlay.Simulator.apply(configuration)))
    val simulator: EntityRef[com.overlay.Simulator.SimulatorCommand] = sharding.entityRefFor(TypeKey, "simulator")
    
    //endpoints
    val route =
        post {
          path("configuration") {
            entity(as[Configuration]) {
              config =>
             
              1 to config.numberOfComputers foreach (i =>
                system.scheduler.scheduleOnce(
                  (2 * i).seconds,
                  () => simulator ! JoinNode(i.toString)
                )
              )
                simulator ! Simulator.SetConfiguration(config)
              config.listOfItems foreach(movie => system.scheduler.scheduleOnce((3).seconds, () => simulator ! Simulator.Write(movie,System.currentTimeMillis())))
              println("Waiting for movies to store ...")
              Thread.sleep(150*config.listOfItems.length)


              system.scheduler.scheduleOnce(
                5 * (config.numberOfComputers + 1).seconds,
                () => simulator ! Simulator.GiveState()
              )
              system.scheduler.scheduleOnce(config.durationOfSimulator.second, ()=> System.exit(1))
              complete(
                "Setting actors ... watch YAML dump for time marked log dumps."
              )
            }
          }
        }

        /* store */
        //Client.StoreMovie()
        /*search*/
        //FindMovie
    // if the config file is given at the start, then sets up network with that, else waits for endpoint post configuration to be hit to set config
    if (configuration.isDefined) {
      1 to configuration.get.numberOfComputers foreach (i =>
        system.scheduler.scheduleOnce(
          (5 * i).seconds,
          () => simulator ! JoinNode(i.toString)
        )
      )
      //system ! Simulator.SetConfiguration(configuration.get)
      val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)
      println(
        s"Server online at http://localhost:8080/\nPress Ctrl+C to stop..."
      )

      bindingFuture
    } else {
      val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

      println(
        s"Server online at http://localhost:8080/\nPress Ctrl+C to stop..."
      )
      bindingFuture
    }
  }
}
