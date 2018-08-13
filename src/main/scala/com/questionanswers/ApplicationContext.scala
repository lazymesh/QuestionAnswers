package com.questionanswers

import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.questionanswers.QuestionEventPublisher._
import com.questionanswers.QuestionEventSubscriber.Get
import com.questionanswers.models.{MutationError, Question}
import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class ApplicationContext(dao: DAO, questionEventSubscriber: ActorRef, questionEventPublisher: ActorRef, questionEventStorePublisher: Publisher[Question]) {
  implicit val timeout = Timeout(10.seconds)

  lazy val eventStream: Source[Question, NotUsed] =
    Source.fromPublisher(questionEventStorePublisher).buffer(100, OverflowStrategy.fail)

  def createQuestion(question: String, answer: String, postedBy: Int) = {
    val questionCreated = dao.createQuestion(question, answer, postedBy)
    questionCreated.onComplete {
      case Success(question) => (questionEventPublisher ? AddEvent(question)).flatMap {
        case EventAdded(_) =>
          (questionEventSubscriber ? Get(question.text, question.postedBy)).mapTo[Option[Question]]
      }
      case Failure(error) => throw MutationError("Service is overloaded.")
    }
    questionCreated
  }

}
