package com.questionanswers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.questionanswers.models.{DateTimeCoerceViolation, Question, User}
import sangria.ast.StringValue
import sangria.execution.deferred._
import sangria.schema.{Field, ObjectType, Schema, _}

object GraphQLSchema {

  implicit val hasQuextionText: HasId[Question, String] = HasId(_.text)
  implicit val hasUserId: HasId[User, Int] = HasId(_.id)

  implicit val GraphQLDateTime = ScalarType[LocalDateTime](
    "DateTime",
    coerceOutput = (dt, _) => dt.toString,
    coerceInput = {
      case StringValue(dt, _, _) => Right(LocalDateTime.parse(dt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
      case _ => Left(DateTimeCoerceViolation)
    },
    coerceUserInput = {
      case s: String => Right(LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
      case _ => Left(DateTimeCoerceViolation)
    }
  )

  val questionType = ObjectType[Unit, Question](
    "Question",
    fields[Unit, Question](
      Field("text", StringType, resolve = _.value.text),
      Field("answer", StringType, resolve = _.value.answer),
      Field("postedBy", IntType, resolve = _.value.postedBy),
      Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt)
    )
  )

  val userType = ObjectType[ApplicationContext, User](
    "User",
    fields[ApplicationContext, User](
      Field("id", IntType, resolve = _.value.id),
      Field("name", StringType, resolve = _.value.name),
      Field("questions", ListType(questionType), resolve = c => questionsFetcher.deferRelSeq(questionByUserRel, c.value.id)),
      Field("lastQuestion", questionType, resolve = c => c.ctx.dao.getLatestQustionByUserId(c.value.id))
    )
  )

  val questionByUserRel = Relation[Question, Int]("questionByUser", q => Seq(q.postedBy))

  val usersFetcher = Fetcher((ctx: ApplicationContext, ids: Seq[Int]) => ctx.dao.getUsers(ids))
  val questionsFetcher = Fetcher.rel(
    (ctx: ApplicationContext, texts: Seq[String]) => ctx.dao.getQuestions(texts),
    (ctx: ApplicationContext, ids: RelationIds[Question]) => ctx.dao.getQuestionsByUser(ids(questionByUserRel))
  )

  val Resolver = DeferredResolver.fetchers(usersFetcher, questionsFetcher)

  val query = ObjectType(
    "Query",
    fields[ApplicationContext, Unit](
      Field("users", ListType(userType), resolve = c => c.ctx.dao.getAllUsers()),
      Field("user", OptionType(userType), arguments = List(Argument("id", IntType)), resolve = c => usersFetcher.deferOpt(c.arg[Int]("id"))),
      Field("questions", ListType(questionType), resolve = c => c.ctx.dao.getAllQuestions()),
      Field("question", OptionType(questionType), arguments = List(Argument("text", StringType)), resolve = c => questionsFetcher.deferOpt(c.arg[String]("text"))),
    )
  )

  val mutation = ObjectType(
    "mutation",
    fields[ApplicationContext, Unit](
      Field("createUser", userType, arguments = List(Argument("id", IntType),Argument("name", StringType)), resolve = c => c.ctx.dao.createUser(c.arg[Int]("id"), c.arg[String]("name"))),
      Field("createQuestion", questionType, arguments = List(Argument("text", StringType),Argument("answer", StringType),Argument("postedBy", IntType)), resolve = c => c.ctx.dao.createQuestion(c.arg[String]("text"), c.arg[String]("answer"), c.arg[Int]("postedBy")))
    )
  )


  val schema = Schema(query, Some(mutation))

}
