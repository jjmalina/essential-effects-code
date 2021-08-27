package com.innerproduct.ee.control

import cats.effect._
import com.innerproduct.ee.debug._
import scala.concurrent.duration._

object JoinAfterStart extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    for {
      fiber <- task.start // <1>
      _ <- IO("pre-join").debug // <3>
      _ <- fiber.join.debug
      _ <- IO("post-join").debug // <3>
    } yield ExitCode.Success

  val task: IO[String] =
    IO.sleep(2.seconds) *> IO("task").debug
}
