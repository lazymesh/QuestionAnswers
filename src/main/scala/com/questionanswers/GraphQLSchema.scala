package com.questionanswers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.stream.Materializer
import com.questionanswers.Server.materializer
import com.questionanswers.models._
import sangria.ast.StringValue
import sangria.execution.deferred._
import sangria.schema.{Field, ObjectType, Schema, _}
import sangria.streaming.akkaStreams._

import scala.concurrent.ExecutionContext

object GraphQLSchema {

  implicit val hasQuextionText: HasId[Question, String] = HasId(_.text)
  implicit val hasUserId: HasId[User, Int] = HasId(_.userId)

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


  val questionByUserRel = Relation[Question, Int]("questionByUser", q => Seq(q.postedBy))

  val usersFetcher = Fetcher((ctx: ApplicationContext, ids: Seq[Int]) => ctx.dao.getUsers(ids))
  val questionsFetcher = Fetcher.rel(
    (ctx: ApplicationContext, texts: Seq[String]) => ctx.dao.getQuestions(texts),
    (ctx: ApplicationContext, ids: RelationIds[Question]) => ctx.dao.getQuestionsByUser(ids(questionByUserRel))
  )

  val Resolver = DeferredResolver.fetchers(usersFetcher, questionsFetcher)


  def schema(implicit ec: ExecutionContext, mat: Materializer) = {

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
        Field("userId", IntType, resolve = _.value.userId),
        Field("name", StringType, resolve = _.value.name),
        Field("questions", ListType(questionType), resolve = c => questionsFetcher.deferRelSeq(questionByUserRel, c.value.userId)),
        Field("lastQuestion", questionType, resolve = c => c.ctx.dao.getLatestQustionByUserId(c.value.userId))
      )
    )

    val query = ObjectType(
      "Query",
      fields[ApplicationContext, Unit](
        Field("users", ListType(userType), resolve = c => c.ctx.dao.getAllUsers()),
        Field("user", OptionType(userType), arguments = List(Argument("userId", IntType)), resolve = c => usersFetcher.deferOpt(c.arg[Int]("userId"))),
        Field("questions", ListType(questionType), resolve = c => c.ctx.dao.getAllQuestions()),
        Field("question", OptionType(questionType), arguments = List(Argument("text", StringType)), resolve = c => questionsFetcher.deferOpt(c.arg[String]("text")))
      )
    )

    val mutation = ObjectType(
      "mutation",
      fields[ApplicationContext, Unit](
        Field("createUser", userType, arguments = List(Argument("userId", IntType), Argument("name", StringType)), resolve = c => c.ctx.createUser(c.arg[Int]("userId"), c.arg[String]("name"))),
        Field("createQuestion", questionType, arguments = List(Argument("text", StringType), Argument("answer", StringType), Argument("postedBy", IntType)), resolve = c => c.ctx.createQuestion(c.arg[String]("text"), c.arg[String]("answer"), c.arg[Int]("postedBy")))
      )
    )

    val subscription = ObjectType(
      "subscription",
      fields[ApplicationContext, Unit](
        Field.subs("questionCreated", questionType, resolve = (c: Context[ApplicationContext, Unit]) => c.ctx.eventStream.filter(event => questionType.valClass.isAssignableFrom(event.getClass)).map(event => Action(event.asInstanceOf[Question]))),
        Field.subs("userCreated", userType, resolve = (c: Context[ApplicationContext, Unit]) => c.ctx.eventStream.filter(event => userType.valClass.isAssignableFrom(event.getClass)).map(event => Action(event.asInstanceOf[User])))
      )
    )
    Schema(query, Some(mutation), Some(subscription))
  }

}
