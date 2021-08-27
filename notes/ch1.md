# Chapter 1. Effects: evaluation and execution

When we write code that is referentially transparent, meaning that two programs are equivalent if they always evaluate to the same value, then we can use substitution to evaluate those programs.

However, the substitution model of evaluation does not work when interacting with the environment of a program such as reading from or writing to a console or socket. It also doesn't work when code relies on mutable variables outside the scope of its definition. In order for these side-effects to not afect evaluation, we have to localize them.

## The Effect Pattern

1. The type of the program should tell us what kind of effects the program will perform, in addition to the type of the value it will produce.

2. If the behavior we want relies upon some externally-visible side effect, we separate describing the effects we want to happen from actually making them happen. We can freely substitute the description of effects until the point we run them.

### Is `Option` an effect?

```scala
sealed trait Option[+A]
case class Some[A](value: A) extends Option[A]
case object None extends Option[Nothing]
```

1. Yes, `Option[A]` represents whether a value of type `A` exists or not.
2. According to this definition of code, it's not an effect because externally-visible side effects are not required. However, since there is a `get` method which can throw `NoSuchElementException("None.get")`, that's an externally-visible side effect but it's separately defined from the description of the effect. Therefore `Option[A]` is an effect.

Therefore `Option[A]` is an effect.

### Is `Future` an effect?

1. Yes. A `Future[A]` represents an asynchronous computation that returns a value of type `A`. A future could succeed or fail.
2. Externally-visible side effects are required, and a future can do anything. It also requires an ExecutionContext. The side effects of a future are executed immediately when constructed. Therefore since `Future` does not separate description from execution it's *unsafe*.

## Capturing arbitrary side effects as an effect

We can create a type for an effect:

```scala
case class MyIO[A](unsafeRun: () => A) {
  def map[B](f: A => B): MyIO[B] =
    MyIO(() => f(unsafeRun())) 1

  def flatMap[B](f: A => MyIO[B]): MyIO[B] =
    MyIO(() => f(unsafeRun()).unsafeRun()) 2
}

object MyIO {
  def putStr(s: => String): MyIO[Unit] =
    MyIO(() => println(s))
}
```

1. `MyIO[A]` represents a possibly side effecting computation which will produce a value of type `A`
2. Externally-visible effects are required. We can construct `MyIO[A]` values and compose them with `map` and `flatMap` but the execution only happens when `unsafeRun` is called.

Therefore `MyIO[A]` is an effect.
