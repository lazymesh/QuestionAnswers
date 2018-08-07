package com.questionanswers

import java.time.LocalDateTime

import sangria.validation.Violation

package object models {

  case class Question(text: String, answer: String, postedBy: Int, createdAt: LocalDateTime)

  case class User(userId: Int, name: String)

  case object DateTimeCoerceViolation extends Violation {
    override def errorMessage: String = "Error during parsing DateTime"
  }

}
