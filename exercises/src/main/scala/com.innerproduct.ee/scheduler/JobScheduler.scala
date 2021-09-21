package com.innerproduct.ee.scheduler

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._
import cats.data.Chain
import scala.concurrent.duration._
import com.innerproduct.ee.debug._

trait JobScheduler {
  def schedule(task: IO[_]): IO[Job.Id]
}

object JobScheduler {
  case class State(
    maxRunning: Int,
    scheduled: Chain[Job.Scheduled] = Chain.empty,
    running: Map[Job.Id, Job.Running] = Map.empty,
    completed: Chain[Job.Completed] = Chain.empty
  ) {
    def enqueue(job: Job.Scheduled): State =
      copy(scheduled = scheduled :+ job)

    def dequeue: (State, Option[Job.Scheduled]) =
      if (running.size >= maxRunning) this -> None
      else
        scheduled.uncons.map {
          case (head, tail) => copy(scheduled = tail) -> Some(head)
        }
        .getOrElse(this -> None)

    def running(job: Job.Running): State =
      copy(running = running + (job.id -> job))

    def onComplete(job: Job.Completed): State =
      copy(completed = completed :+ job)
  }

  def resource(maxRunning: Int)(
    implicit cs: ContextShift[IO]
  ): IO[Resource[IO, JobScheduler]] = {
    for {
      schedulerState <- Ref[IO].of(JobScheduler.State(maxRunning))
      zzz <- Zzz()
      scheduler = new JobScheduler {
        def schedule(task: IO[_]): IO[Job.Id] =
          for {
            _ <- IO("scheduler creating job").debug
            job <- Job.create(task)
            _ <- IO("scheduler updating state").debug
            _ <- schedulerState.update(_.enqueue(job))
            _ <- IO("scheduler waking up reactor").debug
            _ <- zzz.wakeUp
          } yield job.id
      }
      onStart = (id: Job.Id) => IO("onStart").debug *> IO.unit
      onComplete = (id: Job.Id, exitCase: ExitCase[Throwable]) => IO("onComplete").debug *> zzz.wakeUp
      reactor = Reactor(schedulerState, onStart, onComplete)
      loop = (
        IO("loop started").debug *>
        zzz.sleep *>
        IO("loop, awake").debug *>
        reactor.whenAwake(onStart, onComplete) *>
        IO("loop end").debug
      ).foreverM
    } yield loop.background.as(scheduler)
  }
}

object JobSchedulerExample extends IOApp {

  import java.util.UUID

  def run(args: List[String]): IO[ExitCode] =
    for {
      resource <- JobScheduler.resource(maxRunning = 2)
      _ <- resource.use { scheduler =>
        val tasks = (1 to 10).map(_ => generateTask).toList
        tasks.traverse(scheduler.schedule)
      }
      _ <- IO.sleep(30.seconds)
      _ <- IO("done!").debug
    } yield ExitCode.Success

  def generateTask: IO[Unit] = {
    val id = UUID.randomUUID().toString()
    IO(s"<task $id> started").debug *>
      IO.sleep(1.seconds) *>
      IO(s"<task $id> done").debug.void
  }
}
