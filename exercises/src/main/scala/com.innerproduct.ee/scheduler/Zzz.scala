package com.innerproduct.ee.scheduler

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._
import scala.concurrent.duration._
import com.innerproduct.ee.debug._

trait Zzz {
  def sleep: IO[Unit]
  def wakeUp: IO[Unit]
}

object Zzz {
  def apply()(implicit cs: ContextShift[IO]): IO[Zzz] =
    for {
      whenDone <- Deferred[IO, Unit]
      state <- Ref[IO].of[State](Asleep(whenDone))
    } yield new Zzz {
      def sleep: IO[Unit] = state.get.flatMap { // TODO we need to reset to asleep when awake then asleep
        case Asleep(whenDone) => {
          IO("Zzz: Asleep, returning whenDone.get").debug *> whenDone.get
        }
        case Awake() => {
          for {
            newWhenDone <- Deferred[IO, Unit]
            _ <- state.modify {
              case Awake() => Asleep(newWhenDone) -> newWhenDone.get
              case Asleep(wd) => Asleep(newWhenDone) -> newWhenDone.get
            }
          } yield state.get.flatMap {
            case Asleep(wd) => IO("Zzz: Asleep, returning whenDone.get").debug *> wd.get
            case Awake() => IO("Zzz: Awake, returning unit").debug *> IO.unit
          }
        }
      }
      def wakeUp: IO[Unit] = IO("Zzz: waking up") *> state.modify {
        case Asleep(whenDone) => Awake() -> whenDone.complete()
        case Awake() => Awake() -> IO.unit
      }.flatten.uncancelable
    }

  sealed trait State

  case class Asleep(whenDone: Deferred[IO, Unit]) extends State
  case class Awake() extends State
}

object ZzzExample extends IOApp {
  import com.innerproduct.ee.debug._

  def run(args: List[String]): IO[ExitCode] =
    for {
      zzz <- Zzz()
      _ <- (actionWithPrerequisites(zzz), runPrerequisite(zzz)).parTupled
    } yield ExitCode.Success

  def runPrerequisite(zzz: Zzz): IO[Unit] =
    for {
      result <- IO("prerequisite").debug
      _ <- IO.sleep(2.second)
      _ <- IO("Zzz waking up").debug
      _ <- zzz.wakeUp // <1>
      _ <- IO("Zzz awake").debug
      _ <- IO.sleep(2.second)
      _ <- runPrerequisite(zzz)
    } yield ()

  def actionWithPrerequisites(zzz: Zzz): IO[Unit] =
    for {
      _ <- IO("waiting for prerequisites").debug
      _ <- zzz.sleep // <1>
      result <- IO("action").debug // <2>
      _ <- IO("action done going back to sleep")
      _ <- zzz.sleep
      _ <- actionWithPrerequisites(zzz)
    } yield ()
}
