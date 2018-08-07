package com.questionanswers

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import spray.json._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import scala.concurrent.Await
import scala.language.postfixOps

object Server extends App {

  val config = ConfigFactory.load()

  val host = config.getString("http.host")
  val port = config.getInt("http.port")

  implicit val actorSystem = ActorSystem("graphql-questionanswer-server")
  implicit val materializer = ActorMaterializer()

  import actorSystem.dispatcher

  import scala.concurrent.duration._

  scala.sys.addShutdownHook(() -> shutdown())

  val route: Route =
    (cors() & path("graphql")) {
    entity(as[JsValue]) {
      requestJson => GraphQLServer.endpoint(requestJson)
    }
  } ~ {
    getFromResource("graphiql.html")
  }

  Http().bindAndHandle(route, host, port)
  println(s"open a browser with URL: http://$host:$port")


  def shutdown(): Unit = {
    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, 30 seconds)
  }

}
