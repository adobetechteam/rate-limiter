# Flawed Rate Limiter

A deliberately flawed rate limiter implementation in Java. With the help of AI tools, you are expected to:

1. **Find flaws** — identify issues in the implementation, and write tests that expose each one.
2. **Fix them** — propose and implement a fix for each flaw, with passing tests that demonstrate correctness.
3. **Analyze complexity** — compare time and space complexity of the original and fixed implementations.

## Getting Started

**Requirements:** Java 1.8+, Maven 3.3+

```bash
mvn compile          # compile
mvn test             # run tests
```

## The Exercise

### Reported issues

Support has flagged the following. This list may not be exhaustive — there could be additional issues not yet reported.

- Under concurrent load, some users get allowed more requests than the configured limit. Works fine under light or sequential traffic.
- The service has occasionally frozen under concurrent load and needed a restart. Doesn't happen under light load.
- Memory usage climbs over time and never drops, even when traffic is quiet.
- Latency rises as the number of distinct users grows, even though each user's own history stays small.
- Requests right at a window boundary sometimes don't match a manual recount.

### Existing tests

The test file `FlawedRateLimiterTest` contains four baseline tests that pass today:

- `testBasicRateLimiting_Success` — sequential single-user rate limiting
- `testDifferentUsers_Success` — multiple users with separate limits
- `testTimeWindowReset_Success` — window reset using a controllable clock
- `testDisabledRateLimiter_Success` — bypass when disabled

These are examples, not a spec — passing them does not mean the implementation is correct. The `FAILURE CASES` section is intentionally empty; writing tests there that expose the reported issues is part of the exercise.

### Constraints on tests you write

- **No real time** — no `Thread.sleep`, no system clock reads. Use the injectable `Clock` (see `MutableClock` in the test support directory).
- **No non-determinism** — no random data, no reliance on thread scheduling order for correctness.

### Submission

Include a short written summary alongside your code and tests:

- Which flaws you found and how you confirmed each one.
- The fix you chose for each, and why.
- Time and space complexity of the original vs. fixed implementation, if applicable.

This summary can evolve as your understanding changes — it doesn't need to be final before you start coding.

## Project Structure

```
src/
├── main/java/com/example/ratelimiter/
│   ├── RateLimiter.java            # interface
│   ├── FlawedRateLimiter.java      # implementation under test
│   ├── ExampleUsage.java           # basic demo (run with mvn exec:java)
│   └── LoadReproduction.java       # concurrent stress tool
└── test/java/com/example/ratelimiter/
    ├── FlawedRateLimiterTest.java   # test file (add your tests here)
    └── support/
        └── MutableClock.java        # controllable clock for tests
```

## Reproducing Concurrency Issues Under Load

```bash
mvn -q compile
mvn exec:java -Dexec.mainClass="com.example.ratelimiter.LoadReproduction"
```

This runs many threads hitting a shared rate limiter concurrently and prints raw counts (attempts, allowed, internal counters, per-user tallies). Numbers that don't add up, or a run that hangs, are signal. This is a manual stress tool, not a unit test — it is not run by `mvn test`.

## Docker

```bash
docker build -t rate-limiter .
docker run --rm rate-limiter                    # runs ExampleUsage
```

With Docker Compose:

```bash
docker-compose up --build                       # run the application
docker-compose --profile test run --rm \
  rate-limiter-test                             # run tests
docker-compose down                             # clean up
```
