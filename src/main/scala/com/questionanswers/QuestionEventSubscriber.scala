package com.questionanswers

import akka.actor.{ActorLogging, ActorRef}
import akka.stream.actor.ActorSubscriberMessage._
import akka.stream.actor.{ActorSubscriber, OneByOneRequestStrategy}
import com.questionanswers.models.Question

import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.language.postfixOps

class QuestionEventSubscriber extends ActorSubscriber with ActorLogging {
  import QuestionEventSubscriber._

  private var entities = ListMap.empty[String, Question]
  private var waiting = Map.empty[(String, Int), ActorRef]

  import context.dispatcher

  def receive = {
    case OnNext(event: Question) =>
      println("on next of subscriber is called ")
      entities = entities + (event.text -> event)

      val waitingKey = event.text → event.postedBy

      waiting.get(waitingKey) foreach { senderRef =>
        senderRef ! entities.get(event.text)
        waiting = waiting.filterNot(_._1 == waitingKey)
      }
    case RemoveWaiting(key) =>
      waiting.get(key) foreach { senderRef ⇒
        senderRef ! None
        waiting = waiting.filterNot(_._1 == key)
      }
      case Get(text, postedBy) =>
      entities.get(text) match {
        case Some(entity) =>
          sender() ! entities.get(text)
        case _ =>
          waiting = waiting + ((text → postedBy) → sender())
          context.system.scheduler.scheduleOnce(5 seconds, self, RemoveWaiting(text → postedBy))
      }
  }

  val requestStrategy = OneByOneRequestStrategy

}

object QuestionEventSubscriber {
  case class Get(text: String, postedBy: Int)

  private case class RemoveWaiting(key: (String, Int))
}