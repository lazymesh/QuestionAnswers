package com.questionanswers

import java.time.LocalDateTime

import sangria.execution.UserFacingError
import sangria.validation.Violation

package object models {

  sealed trait Events

  case class Question(text: String, answer: String, postedBy: Int, createdAt: LocalDateTime) extends Events

  case class User(userId: Int, name: String) extends Events

  case class PageInfo(hasNextPage: Boolean, hasPreviousPage: Boolean, startCursor: String, endCursor: String)

  case class QuestionEdge(node: Question, cursor: String)

  case class QuestionConnection(pageInfo: PageInfo, edges: Seq[QuestionEdge], count: Int)

  case object DateTimeCoerceViolation extends Violation {
    override def errorMessage: String = "Error during parsing DateTime"
  }

  case class MutationError(message: String) extends Exception(message) with UserFacingError

}
