package com.overlay
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.{DefaultJsonProtocol, JsonFormat}
import com.overlay.Movie
import com.overlay.Configuration

object Marshallers extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val movieFormat = jsonFormat3(Movie)
  implicit val configurationFormat = jsonFormat10(Configuration)
}
