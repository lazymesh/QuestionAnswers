package com.questionanswers

import akka.actor.{ActorLogging, ActorRef}
import akka.stream.actor.ActorSubscriberMessage._
import akka.stream.actor.{ActorSubscriber, OneByOneRequestStrategy}
import com.questionanswers.models.{Events, Question, User}

import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.language.postfixOps

class EventSubscriber extends ActorSubscriber with ActorLogging {
  import EventSubscriber._

  private var entities = ListMap.empty[String, Events]
  private var waiting = Map.empty[(String, Int), ActorRef]

  import context.dispatcher

  def receive = {
    case OnNext(event: Events) =>
      case event: Question =>
        entities = entities + (event.text -> event)

        val waitingKey = event.text → event.postedBy

        waiting.get(waitingKey) foreach { senderRef =>
          senderRef ! entities.get(event.text)
          waiting = waiting.filterNot(_._1 == waitingKey)
        }
      case event: User =>
        entities = entities + (event.name -> event)

        val waitingKey = event.name → event.userId

        waiting.get(waitingKey) foreach { senderRef =>
          senderRef ! entities.get(event.name)
          waiting = waiting.filterNot(_._1 == waitingKey)
        }

    case RemoveWaiting(key) =>
      waiting.get(key) foreach { senderRef ⇒
        senderRef ! None
        waiting = waiting.filterNot(_._1 == key)
      }

    case GetQuestion(text, postedBy) => get(text, postedBy)

    case GetUser(name, userId) => get(name, userId)

  }
  def get(key1: String, key2: Int) = {
    entities.get(key1) match {
      case Some(entity) =>
        sender() ! entities.get(key1)
      case _ =>
        waiting = waiting + ((key1 → key2) → sender())
        context.system.scheduler.scheduleOnce(5 seconds, self, RemoveWaiting(key1 → key2))
    }
  }

  val requestStrategy = OneByOneRequestStrategy

}

object EventSubscriber {
  case class GetQuestion(text: String, postedBy: Int)
  case class GetUser(name: String, userId: Int)

  private case class RemoveWaiting(key: (String, Int))
}