# Chapter 8. Testing effects

To test effects, there are two approaches:

1. Test the values produced by an effect by executing it
2. Control how effects interact with their runtime dependencies like `ExecutionContext`

## Assertions on effectful values

We need to invoke one of the `unsafe` methods of `IO`

```scala
def assertGreaterThanZero(i: IO[Int]) =
  assert(i.unsafeRunSync() > 0)
```

You could also make the assertion inside:

```scala
def assertGreaterThanZero(i: IO[Int]) =
  i.map(j => assert(j > 0)).unsafeRunSync()
```

### Faking effects with interfaces

We can create fake implementations for dependencies of effect composition, and then run the effect and assert on its behavior:

```scala
def registrationFailsIfEmailDeliveryFails(email: EmailAddress) =
  new UserRegistration(new FailingEmailDelivery)
    .send(email)
    .attempt
    .map(result => assert(result.isLeft, s"expecting failure, but was $result"))
    .unsafeRunSync
```


## Testing effect scheduling by controlling its dependencies

The `TestContext` helper class lets us use faked `ExecutionContext` and `Timer` instances in the effectful code we want to test.

```scala
import cats.effect.laws.util.TestContext

val ctx = TestContext()

implicit val cs: ContextShift[IO] = ctx.ioContextShift
implicit val timer: Timer[IO] = ctx.timer

val timeoutError = new TimeoutException
val timeout = IO.sleep(10.seconds) *> IO.raiseError[Int](timeoutError)
val f = timeout.unsafeToFuture()

// Not yet
ctx.tick(5.seconds)
assertEquals(f.value, None)

// Good to go:
ctx.tick(5.seconds)
assertEquals(f.value, Some(Failure(timeoutError)))
```
