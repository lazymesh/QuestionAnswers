package com.questionanswers

import com.typesafe.config.Config
import monix.eval.Task
import monix.execution.Scheduler
import org.neo4j.driver.v1.{AuthTokens, Driver, GraphDatabase}

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

object DBSchema {

  def createDatabase(config: Config): DAO = {
    implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global
    val neo4jConnection = createNeo4jConnection(config)
    new DAO(neo4jConnection)
  }

  /**
    * The problem, that it takes ~15 seconds for Neo4j to initialize all internal services in a docker container.
    * So, without the retry policy the test will fail due to missing connection with Neo4j instance.
    */
  private def createNeo4jConnection(config: Config)(implicit s: Scheduler): Driver = {
    println(s"Creating Neo4j connection with config ${config.getConfig("neo4j")}")

    import scala.concurrent.duration._

    def connectionAttempt() = Task {
      GraphDatabase.driver(config.getString("neo4j.uri"), AuthTokens.basic(config.getString("neo4j.username"), config.getString("neo4j.password")))
    }

    val result = retry("Acquire Neo4j connection", connectionAttempt(), 5.seconds, 10.seconds, 10)

    Await.result(result.runAsync, 55.seconds)
  }

  private def retry[T](name: String, originalTask: => Task[T], delay: FiniteDuration, timeout: FiniteDuration, retries: Int): Task[T] = {
    val delayedTask = originalTask.delayExecution(delay).timeout(timeout)

    def loop(task: Task[T], retries: Int): Task[T] = {
      task.onErrorRecoverWith { case error if retries > 0 =>
        println(s"[$name] Retry policy. Current retires [$retries]. Delay [$delay]. Error [${error.getMessage}]")
        loop(task, retries - 1)
      }
    }

    loop(delayedTask, retries)
  }

}
