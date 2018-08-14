package com.questionanswers

import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import com.questionanswers.models.Events

class EventPublisher extends ActorPublisher[Events] {
  import EventPublisher._

  var events = Vector.empty[Events]

  var eventBuffer = Vector.empty[Events]

  def receive = {
    case AddEvent(event) =>
      addEvent(event)
      sender() ! EventAdded(event)

    case Request(_) => {
      deliverEvents()
    }
    case Cancel => {
      context.stop(self)
    }
  }

  def addEvent(event: Events) = {
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

object EventPublisher {
  case class AddEvent(event: Events)

  case class EventAdded(event: Events)
}