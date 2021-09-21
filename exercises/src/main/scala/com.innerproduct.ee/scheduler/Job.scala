package com.innerproduct.ee.scheduler

import java.util.UUID

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._
import com.innerproduct.ee.debug._


sealed trait Job

object Job {
  case class Id(value: UUID) extends AnyVal

  case class Scheduled(id: Id, task: IO[_]) extends Job {
    def start(implicit cs: ContextShift[IO]): IO[Job.Running] =
      for {
        _ <- IO("Scheduled starting", id, task).debug
        exitCase <- Deferred[IO, ExitCase[Throwable]]
        _ <- IO("Scheduled got exitCase").debug
        fiber <- task.void.guaranteeCase(exitCase.complete).start
        _ <- IO("Scheduled fiber started").debug
      } yield Running(id, fiber, exitCase)
  }

  case class Running(
    id: Id,
    fiber: Fiber[IO, Unit],
    exitCase: Deferred[IO, ExitCase[Throwable]]
  ) extends Job {
    val await: IO[Completed] = exitCase.get.map(Completed(id, _))
  }

  case class Completed(id: Id, exitCase: ExitCase[Throwable]) extends Job

  def create[A](task: IO[A]): IO[Scheduled] =
    IO(Id(UUID.randomUUID())).map(Scheduled(_, task))
}
