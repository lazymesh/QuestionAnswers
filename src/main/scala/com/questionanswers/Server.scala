package com.questionanswers

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl.{Sink, Source}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.questionanswers.models.Events
import com.typesafe.config.ConfigFactory
import sangria.execution.Executor
import spray.json._

import scala.concurrent.Await
import scala.language.postfixOps

object Server extends App {

  val config = ConfigFactory.load()

  val host = config.getString("http.host")
  val port = config.getInt("http.port")

  implicit val actorSystem = ActorSystem("graphql-questionanswer-server")
  implicit val materializer = ActorMaterializer()

  import actorSystem.dispatcher

  val subscriber =actorSystem.actorOf(Props[EventSubscriber])
  val eventSubscriber = Sink.fromSubscriber(ActorSubscriber[Events](subscriber))

  val eventPublisher = actorSystem.actorOf(Props[EventPublisher])
  val evetStorePublisher = Source.fromPublisher(ActorPublisher[Events](eventPublisher)).runWith(Sink.asPublisher(fanout = true))

  Source.fromPublisher(evetStorePublisher).collect{case event: Events ⇒ event}.to(eventSubscriber).run()

  val executor = Executor(GraphQLSchema.schema, deferredResolver = GraphQLSchema.Resolver)

  val dao = DBSchema.createDatabase(config)
  val ctx = ApplicationContext(dao, subscriber, eventPublisher, evetStorePublisher)

  import scala.concurrent.duration._

  scala.sys.addShutdownHook(() -> shutdown())

  val route: Route =
    (cors() & path("graphql")) {
      entity(as[JsValue]) {
        requestJson => GraphQLServer.endpoint(requestJson)
      }
    } ~
      cors() {
        getFromResource("graphiql.html")
      }

  Http().bindAndHandle(route, host, port)
  println(s"open a browser with URL: http://$host:$port")


  def shutdown(): Unit = {
    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, 30 seconds)
  }

}
