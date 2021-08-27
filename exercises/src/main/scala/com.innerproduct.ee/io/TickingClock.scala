package com.innerproduct.ee.io

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import cats.effect._

object TickingClock extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    tickingClock.as(ExitCode.Success)

  def loop: IO[Unit] =
    for {
      t <- IO(System.currentTimeMillis)
      _ <- IO(println(t))
      _ <- IO.sleep(FiniteDuration(1, TimeUnit.SECONDS))
      _ <- loop
    } yield ()

  val tickingClock: IO[Unit] = loop // <1>

}
