# Chapter 6. Integrating asynchrony

We can use `IO.async` to turn asynchronous effects like `scala.concurrent.Future` into `IO` values.

The type signature of `IO.async` is:

```scala
def async[A](k: (Either[Throwable, A] => Unit) => Unit): IO[A]
```

You pass it a function which takes a callback function of signature `Either[Throwable, A] => Unit` and returns a unit. You have to call the callback with `Either[Throwable, A]` to complete the async call. e.g.

```scala
def synchronousSum(l: Int, r: Int): IO[Int] =
  IO.async { cb =>
    cb(Right(l + r))
  }
```

## Integrating with `Future`

Example:

```scala
def asFuture(): Future[String] =
  Future.successful("woo!")

val asIO: IO[String] = IO.fromFuture(IO(asFuture))
```

**Why does `IO.fromFuture` require a `Future` inside an `IO`?**

Creating a `Future` has a side effect: it schedules the `Future` to be executed. In order to delay this we must wrap the creation of the `Future` in an `IO`
