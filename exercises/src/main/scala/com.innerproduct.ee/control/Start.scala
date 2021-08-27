package com.innerproduct.ee.control

import cats.effect._
import com.innerproduct.ee.debug._

object Start extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    for {
      fiber <- task.start // <1>
      _ <- IO("task was started").debug // <2>
    } yield ExitCode.Success

  val task: IO[String] =
    IO("task").debug
}
