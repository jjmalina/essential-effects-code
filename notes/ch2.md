# Chapter 2. Cats Effect IO

## Constructing `IO` values

`IO.delay` takes a call-by-name (lazily-evaluated) argument, delaying the evaluation of code

`IO.apply` is an alias for `IO.delay`. This lets us write `IO(...)` instead of `IO.delay(...)`, which is shorter.

We can also construct IO values from existing “pure” values: `IO.pure(12)`, but do not perform side effects when calling `IO.pure` because they are eagerly evaluated and that can break substitution.

## Transforming `IO` values

IO is a functor; we can map over it: `IO(12).map(_ + 1)`

IO is an applicative; we can mapN over two or more values: `(IO(12), IO("hi")).mapN((i, s) => s"$s: $i")`

IO is a monad; we can flatMap over it, or more conveniently, we can use a for-comprehension:
```scala
for {
  i <- IO(12)
  j <- IO(i + 1)
} yield j.toString
```

### Error handling

A common combinator for error handling is handleErrorWith, which has a similar signature to flatMap except it accepts error values.

We can instead handle errors by transforming them into `Either` values, so an `IO[A]` now becomes an `IO[Either[Throwable, A]]` by using `attempt`.

### Error-handling Decision Tree

If an error occurs in your IO[A] do you want to...

**perform an effect?**: Use `onError(pf: PartialFunction[Throwable, IO[Unit]]): IO[A]`

**transform any error into another error?** Use `adaptError(pf: PartialFunction[Throwable, Throwable]): IO[A]`

**transform any error into a successful value?** Use `handleError(f: Throwable ⇒ A): IO[A]`

**transform some kinds of errors into a successful value?** Use `recover(pf: PartialFunction[Throwable, A]): IO[A]`

**transform some kinds of errors into another effect?** Use `recoverWith(pf: PartialFunction[Throwable, IO[A]]): IO[A]`

**make errors visible but delay error-handling?** Use `attempt: IO[Either[Throwable, A]]`

Otherwise, use `handleErrorWith(f: Throwable ⇒ IO[A]): IO[A]`

## Executing `IO` values

Invoking unsafeRunSync on an `IO[A]` will produce a value of type `A` if the effect succeeds.

As a general rule, you should not be invoking any unsafe method in your code.

Instead, you’ll delegate this responsibility to types like `IOApp`.

## Executing effects in applications with `IOApp`

```scala
package com.innerproduct.ee.resources

import cats.effect._

object HelloWorld extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    helloWorld.as(ExitCode.Success)

  val helloWorld: IO[Unit] =
    IO(println("Hello world!"))
}
```
