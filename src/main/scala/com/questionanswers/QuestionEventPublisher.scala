package com.questionanswers

import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import com.questionanswers.models.Question

class QuestionEventPublisher extends ActorPublisher[Question] {
  import QuestionEventPublisher._

  // in-memory event storage
  var events = Vector.empty[Question]

  var eventBuffer = Vector.empty[Question]

  def receive = {
    case AddEvent(event) =>
      addEvent(event)
      sender() ! EventAdded(event)

    case Request(_) => deliverEvents()
    case Cancel => context.stop(self)
  }

  def addEvent(event: Question) = {
    events  = events :+ event
    eventBuffer  = eventBuffer :+ event

    deliverEvents()
  }

  def deliverEvents(): Unit = {
    if (isActive && totalDemand > 0) {
      val (use, keep) = eventBuffer.splitAt(totalDemand.toInt)

      eventBuffer = keep

      use foreach onNext
    }
  }
}

object QuestionEventPublisher {
  case class AddEvent(event: Question)

  case class EventAdded(event: Question)

  val MaxBufferCapacity = 1000
}