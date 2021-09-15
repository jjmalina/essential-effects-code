# Chapter 9. Concurrent coordination

## 9.1. Atomic updates with Ref

We can use a `Ref` to share "mutable" state safely.

```scala
import cats.effect._
import cats.effect.concurrent.Ref // <1>
import cats.implicits._
import com.innerproduct.ee.debug._
import scala.concurrent.duration._

object ConcurrentStateRef extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    for {
      ticks <- Ref[IO].of(0L) // <2>
      _ <- (tickingClock(ticks), printTicks(ticks)).parTupled // <3>
    } yield ExitCode.Success

  def tickingClock(ticks: Ref[IO, Long]): IO[Unit] =
    for {
      _ <- IO.sleep(1.second)
      _ <- IO(System.currentTimeMillis).debug
      _ <- ticks.update(_ + 1) // <4>
      _ <- tickingClock(ticks)
    } yield ()

  def printTicks(ticks: Ref[IO, Long]): IO[Unit] =
    for {
      _ <- IO.sleep(5.seconds)
      n <- ticks.get // <5>
      _ <- IO(s"TICKS: $n").debug
      _ <- printTicks(ticks)
    } yield ()
}
```

In this example we have our previous ticking clock which updates the `ticks` `Ref` and we have another program which prints the ticks every 5 seconds. While one program runs and updates the ticks, the other reads on another thread.

`Ref` has methods like `update`, `getAndUpdate`, and `modify` which must be pure and cannot have side-effects, because it's possible that these functions run more than once. What!? Yeah the updates are done optimistically.

```scala
def modify(f: A => (A, B)): IO[B] = {
  @tailrec
  def spin: B = {
    val current = ar.get
    val (updated, b) = f(current)
    if (!ar.compareAndSet(current, updated)) spin
    else b
  }
  IO.delay(spin)
}
```

In `modify`, if the current value is not the same after `f` was called, then it won't be set and the whole update needs to happen again.

## 9.2. Write-once synchronization with `Deferred`

We can use a `Deferred` to do blocking synchronization. e.g. we want to print ticks only when they reach a certain number.

### Synchronization

In Computer Science the term *synchronization* is used to talk about enforcing a relationship between effects. The following are types of synchronization constraints:

**serialization:** effect `B` should only happen *after* effect `A`
**mutual-exclusion:** effect A should *never* happen at the same time as effect `B`

With `IO` via `flatMap` we can express that one effect happens *after* another.

```scala
for {
  a <- effectA
  b <- effectB
} yield a + b
```

On the other hand, `parMapN` expresses no relationship or synchronization between effects other than transforming each effect's output after they've completed:

```scala
(effectA, effectB).parMapN((a, b) => a + b)
```

Complex effects that aren't easily expressed via `flatMap` or `parMapN`:

**updating shared state**: Updates must be atomic, i.e. mutually exclusive with respect to other updates, otherwise updates may be "lost"
**reading shared state**: Concurrent reads don't require any synchronization. We read whatever the "current" value is, independent of anything else.
**blocking**: Subsequent effects must happen after the "blocking" effect "unblocks" (serialization)

## 9.3. Concurrent state machines

The behaviopr modeled by a *latch* is blocking subsequent effects until a certain number of (possibly concurrent) effects have occurred.

The two roles that coordinate through the shared latch:

1. *readers* wait for the latch to open
2. *writers* decrement the latch counter

The latch itself is responsible for "opening" when its counter reaches zero.

```scala
import cats.effect._
import cats.effect.concurrent._
import cats.implicits._

trait CountdownLatch {
  def await: IO[Unit]
  def decrement: IO[Unit]
}

object CountdownLatch {
  def apply(n: Long)(implicit cs: ContextShift[IO]): IO[CountdownLatch] =
    for {
      whenDone <- Deferred[IO, Unit]
      state <- Ref[IO].of[State](Outstanding(n, whenDone))
    } yield new CountdownLatch {
      def await: IO[Unit] =
        state.get.flatMap {
          case Outstanding(_, whenDone) => whenDone.get
          case Done()                   => IO.unit
        }

      def decrement: IO[Unit] =
        state.modify {
          case Outstanding(1, whenDone) => Done() -> whenDone.complete(())
          case Outstanding(n, whenDone) =>
            Outstanding(n - 1, whenDone) -> IO.unit
          case Done() => Done() -> IO.unit
        }.flatten.uncancelable
    }

  sealed trait State
  case class Outstanding(n: Long, whenDone: Deferred[IO, Unit]) extends State
  case class Done() extends State
}

object LatchExample extends IOApp {
  import com.innerproduct.ee.debug._

  def run(args: List[String]): IO[ExitCode] =
    for {
      latch <- CountdownLatch(1)
      _ <- (actionWithPrerequisites(latch), runPrerequisite(latch)).parTupled
    } yield ExitCode.Success

  def runPrerequisite(latch: CountdownLatch) =
    for {
      result <- IO("prerequisite").debug
      _ <- latch.decrement // <1>
    } yield result

  def actionWithPrerequisites(latch: CountdownLatch) =
    for {
      _ <- IO("waiting for prerequisites").debug
      _ <- latch.await // <1>
      result <- IO("action").debug // <2>
    } yield result
}
```
