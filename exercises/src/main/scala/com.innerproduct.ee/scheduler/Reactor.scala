package com.innerproduct.ee.scheduler

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._

import com.innerproduct.ee.debug._


trait Reactor {
  def whenAwake(
    onStart: Job.Id => IO[Unit],
    onComplete: (Job.Id, ExitCase[Throwable]) => IO[Unit]
  ): IO[Unit]
}

object Reactor {
  def apply(
    stateRef: Ref[IO, JobScheduler.State],
    onStart: Job.Id => IO[Unit],
    onComplete: (Job.Id, ExitCase[Throwable]) => IO[Unit]
  )(implicit cs: ContextShift[IO]): Reactor =
    new Reactor {
      def whenAwake(
        onStart: Job.Id => IO[Unit],
        onComplete: (Job.Id, ExitCase[Throwable]) => IO[Unit]
      ): IO[Unit] = {
        IO("Reactor awake").debug *> startNextJob.iterateUntil(_.isEmpty).void
      }

      def startNextJob: IO[Option[Job.Running]] =
        for {
          job <- stateRef.modify(_.dequeue)
          _ <- IO("Reactor dequeued job", job).debug
          running <- job.traverse(startJob)
        } yield running

      def startJob(scheduled: Job.Scheduled): IO[Job.Running] =
        for {
          _ <- IO("Reactor starting job", scheduled).debug
          running <- scheduled.start
          _ <- IO("Reactor running job", running).debug
          _ <- stateRef.update(_.running(running))
          _ <- IO("Reactor updated running state", stateRef).debug
          _ <- registerOnComplete(running)
          _ <- IO("Reactor registered on completed").debug
          _ <- onStart(running.id).attempt
        } yield running

      def registerOnComplete(job: Job.Running) =
        for {
          _ <- IO("Reactor waiting for completed job").debug
          completedJob <- job.await
          _ <- IO("Reactor got completed job").debug
        } yield jobCompleted(completedJob).start

      def jobCompleted(job: Job.Completed): IO[Unit] =
        stateRef
          .update(_.onComplete(job))
          .flatTap(_ => onComplete(job.id, job.exitCase).attempt)
    }
}
