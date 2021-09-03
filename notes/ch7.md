# Chapter 7. Managing resources

In Cats Effect, the `Resource` data type represents a acquire-use-release pattern to manage such as opening/closing files & connections.

We use `Resource.make` which has the following type signature:

```scala
def make[A](acquire: IO[A])(release: A => IO[Unit]): Resource[IO, A]
```

Example:

```scala
val stringResource = Resource.make(
  IO("> acquiring stringResource") *> IO("String")
)(_ => IO("< releaing stringResource"))

stringResource.use { s => IO(s"$s is so cool!") }
```

If `acquire`'s value raises an error, the `release` function will still be called.

## 7.1.2. Example: Canceling a background task

You can turn an `IO` into a task that never ends by calling `foreverM`. If you want to manage this with a `Resource` you could use `Resource.make(loop.start)(_.cancel)` but managing a running task can be tricky. Calling `background` on an `IO` will turn it into a `Resource[IO, IO[A]]`.

## 7.2. Composing managed state

`Resource` is

- a *functor*, you can `map` over it
- an *applicative*, you can call `mapN` over two or more values
- a *monad*, so you can use `flatMap`

## 7.2.1. Parallel resource composition

We can combine two resources into one using `tupled`, but if we wanted them run on different threads it could be done with `parTupled`.
