package com.questionanswers

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{MediaTypes => _}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import sangria.ast.{Document, OperationType}
import sangria.execution.{ErrorWithResolver, QueryAnalysisError}
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import spray.json._

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object GraphQLServer {

  def endpoint(requestJSON: JsValue)(implicit ec: ExecutionContext, mat: ActorMaterializer) = {
    val JsObject(fields) = requestJSON
    val JsString(query) = fields("query")

    QueryParser.parse(query) match {
      case Success(queryAst) =>
        val operation = fields.get("operationName") collect {
          case JsString(op) => op
        }
        val variables = fields.get("variables") match {
          case Some(obj: JsObject) => obj
          case _ => JsObject.empty
        }

        queryAst.operationType(operation) match {
          case Some(OperationType.Subscription) =>

            complete(executeSubscriptionGraphQLQuery(queryAst, operation, variables))
          case _ =>
            complete(executeGraphQLQuery(queryAst, operation, variables))
        }
      case Failure(error) =>
        complete(BadRequest, JsObject("error" -> JsString(error.getMessage)))
    }
  }

  private def executeGraphQLQuery(query: Document, operation: Option[String], vars: JsObject)(implicit ec: ExecutionContext, mat: ActorMaterializer)={
    Server.executor
      .execute(
        query,
        Server.ctx,
        (),
        variables = vars,
        operationName = operation
      ).map(OK -> _)
      .recover{
        case error: QueryAnalysisError => BadRequest -> error.resolveError
        case error: ErrorWithResolver => InternalServerError -> error.resolveError
      }
  }

  private def executeSubscriptionGraphQLQuery(query: Document, operation: Option[String], vars: JsObject)(implicit ec: ExecutionContext, mat: ActorMaterializer)={
    import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
    import akka.http.scaladsl.model.sse.ServerSentEvent
    import sangria.execution.ExecutionScheme.Stream
    import sangria.streaming.akkaStreams._

    Server.executor
        .prepare(
          query,
          Server.ctx,
          (),
          variables = vars,
          operationName = operation
        ).map { preparedQuery =>
            ToResponseMarshallable(preparedQuery.execute()
              .map(result =>
                ServerSentEvent(result.compactPrint))
              .recover{ case NonFatal(error) =>
                println(error, "Unexpected error during event stream processing.")
                ServerSentEvent(error.getMessage)
              })
    }
      .recover {
        case error: QueryAnalysisError ⇒ ToResponseMarshallable(BadRequest → error.resolveError)
        case error: ErrorWithResolver ⇒ ToResponseMarshallable(InternalServerError → error.resolveError)
      }
  }
}
