package com.questionanswers

import java.time.{LocalDateTime, ZoneOffset}

import com.questionanswers.models.{Question, User}
import org.neo4j.driver.v1.{Driver, Record}

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters

class DAO(connection : Driver) {

  def getAllUsers() ={
    val session = connection.session()
    val queryString = s"""match(n: User) return n.id as id, n.name as name"""

    val completion = session
      .runAsync(queryString)
      .thenCompose[java.util.List[User]](c => c.listAsync[User](r => User(r.get("id").asInt(), r.get("name").asString())))
      .thenApply[Seq[User]]{ _.asScala }
      .whenComplete((_, _) => session.closeAsync())

    FutureConverters.toScala(completion)
  }

  def getUsers(ids: Seq[Int]) ={
    val stringIds = ids.mkString(",")
    val session = connection.session()
    val queryString = s"""match(n: User) where n.id in [$stringIds] return n.id as id, n.name as name"""

    val completion = session
      .runAsync(queryString)
      .thenCompose[java.util.List[User]](c => c.listAsync[User](r => User(r.get("id").asInt(), r.get("name").asString())))
      .thenApply[Seq[User]]{ _.asScala }
      .whenComplete((_, _) => session.closeAsync())

    FutureConverters.toScala(completion)
  }

  def getAllQuestions() ={
    val session = connection.session()
    val queryString = s"""match(n: Question) return n.text as text, n.answer as answer, n.postedBy as postedBy, n.createdAt as createdAt"""

    val completion = session
      .runAsync(queryString)
      .thenCompose[java.util.List[Question]](c => c.listAsync[Question](r => Question(r.get("text").asString(), r.get("answer").asString(), r.get("postedBy").asInt(), r.get("createdAt").asLocalDateTime())))
      .thenApply[Seq[Question]]{ _.asScala }
      .whenComplete((_, _) => session.closeAsync())

    FutureConverters.toScala(completion)
  }

  def getQuestions(texts: Seq[String]) ={
    val stringTexts = texts.mkString(",")
    val session = connection.session()
    val queryString = s"""match(n: Question) where "$stringTexts" contains n.text return n.text as text, n.answer as answer, n.postedBy as postedBy, n.createdAt as createdAt"""

    val completion = session
      .runAsync(queryString)
      .thenCompose[java.util.List[Question]](c => c.listAsync[Question](r => Question(r.get("text").asString(), r.get("answer").asString(), r.get("postedBy").asInt(), r.get("createdAt").asLocalDateTime())))
      .thenApply[Seq[Question]]{ _.asScala }
      .whenComplete((_, _) => session.closeAsync())

    FutureConverters.toScala(completion)
  }

  def getQuestionsByUser(ids: Seq[Int]) ={
    val stringPostedIds = ids.mkString(",")
    val session = connection.session()
    val queryString = s"""match(n: Question) where n.postedBy in [$stringPostedIds] return n.text as text, n.answer as answer, n.postedBy as postedBy, n.createdAt as createdAt"""

    val completion = session
      .runAsync(queryString)
      .thenCompose[java.util.List[Question]](c => c.listAsync[Question](r => Question(r.get("text").asString(), r.get("answer").asString(), r.get("postedBy").asInt(), r.get("createdAt").asLocalDateTime())))
      .thenApply[Seq[Question]]{ _.asScala }
      .whenComplete((_, _) => session.closeAsync())

    FutureConverters.toScala(completion)
  }

  def getLatestQustionByUserId(id: Int) ={
    val session = connection.session()
    val queryString = s"""match(n: Question) where n.postedBy = $id return n.text as text, n.answer as answer, n.postedBy as postedBy, n.createdAt as createdAt"""

    val completion = session
      .runAsync(queryString)
      .thenCompose[java.util.List[Question]](c => c.listAsync[Question](r => Question(r.get("text").asString(), r.get("answer").asString(), r.get("postedBy").asInt(), r.get("createdAt").asLocalDateTime())))
      .thenApply[Question]{ _.asScala.sortBy(_.createdAt.toInstant(ZoneOffset.ofTotalSeconds(0)).toEpochMilli()).last }
      .whenComplete((_, _) => session.closeAsync())

    FutureConverters.toScala(completion)
  }

  def createUser(id: Int, name: String) ={
    val session = connection.session()
    val queryString = s"""create(n: User{id: $id, name: "$name"}) return n.id as id, n.name as name"""

    val completion = session
      .runAsync(queryString)
      .thenCompose[Record](c => c.nextAsync())
      .thenApply[User](r => User(r.get("id").asInt(), r.get("name").asString()))
      .whenComplete((_, _) => session.closeAsync())

    FutureConverters.toScala(completion)
  }

  def createQuestion(question: String, answer: String, postedBy: Int) ={
    val session = connection.session()
    val queryString = s"""create(n: Question{text: "$question", answer: "$answer", postedBy: $postedBy, createdAt: $getTodayDateTimeNeo4j}) return n.text as text, n.answer as answer, n.postedBy as postedBy, n.createdAt as createdAt"""

    val completion = session
      .runAsync(queryString)
      .thenCompose[Record](c => c.nextAsync())
      .thenApply[Question](r => Question(r.get("text").asString(), r.get("answer").asString(), r.get("postedBy").asInt(), r.get("createdAt").asLocalDateTime()))
      .whenComplete((_, _) => session.closeAsync())

    FutureConverters.toScala(completion)
  }

  def getTodayDateTimeNeo4j(): String ={
    s"""localdatetime(
       |{date:date({ year:${LocalDateTime.now.getYear}, month:${LocalDateTime.now.getMonthValue}, day:${LocalDateTime.now.getDayOfMonth}}),
       | time: localtime({ hour:${LocalDateTime.now.getHour}, minute:${LocalDateTime.now.getMinute}, second:${LocalDateTime.now.getSecond}})})
       | """.stripMargin
  }

}
