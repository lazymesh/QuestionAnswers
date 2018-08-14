package com.questionanswers

import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.questionanswers.EventPublisher._
import com.questionanswers.EventSubscriber.{GetQuestion, GetUser}
import com.questionanswers.models.{Events, MutationError, Question, User}
import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class ApplicationContext(dao: DAO, eventSubscriber: ActorRef, eventPublisher: ActorRef, eventStorePublisher: Publisher[Events]) {
  implicit val timeout = Timeout(10.seconds)

  lazy val eventStream: Source[Events, NotUsed] =
    Source.fromPublisher(eventStorePublisher).buffer(100, OverflowStrategy.fail)

  def createQuestion(question: String, answer: String, postedBy: Int) = {
    val questionCreated = dao.createQuestion(question, answer, postedBy)
    questionCreated.onComplete {
      case Success(question) => {
        (eventPublisher ? AddEvent(question)).flatMap {
        case EventAdded(_) =>
          (eventSubscriber ? GetQuestion(question.text, question.postedBy)).mapTo[Option[Question]]
      }}
      case Failure(error) => throw MutationError("Service is overloaded.")
    }
    questionCreated
  }

  def createUser(userId: Int, name: String) = {
    val userCreated = dao.createUser(userId, name)
    userCreated.onComplete {
      case Success(user) => (eventPublisher ? AddEvent(user)).flatMap {
        case EventAdded(_) =>
          (eventSubscriber ? GetUser(user.name, user.userId)).mapTo[Option[User]]
      }
      case Failure(error) => throw MutationError("Service is overloaded.")
    }
    userCreated
  }

}
