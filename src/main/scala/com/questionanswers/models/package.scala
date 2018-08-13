package com.questionanswers

import java.time.LocalDateTime

import sangria.execution.UserFacingError
import sangria.validation.Violation

package object models {

  case class Question(text: String, answer: String, postedBy: Int, createdAt: LocalDateTime)

  case class User(userId: Int, name: String)

  case object DateTimeCoerceViolation extends Violation {
    override def errorMessage: String = "Error during parsing DateTime"
  }

  case class MutationError(message: String) extends Exception(message) with UserFacingError

}
