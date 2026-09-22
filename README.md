# Flawed Rate Limiter Project

This is a Maven Java project that demonstrates a deliberately flawed rate limiter implementation. The rate limiter contains multiple intentional issues across different categories.

With help from AI Agents, the interviewee is expected to:
- Identify and explain each flaw in the implementation. Demonstrate them by writing tests that expose the flaws.
- Propose and implement fixes for each flaw, improving the design and robustness of the rate limiter. Explain what the fixes are. Demonstrate the fixes with passing tests.
- Analyze time and space complexity of the original and fixed implementations.

The tests in this repo (`FlawedRateLimiterTest`) are examples, not a full spec — passing them isn't enough. See "Reported Issues" below for what they miss.

## Reported Issues

Support has flagged the following:

- Under concurrent load, some users get allowed more requests than the configured limit. Works fine under light or sequential traffic.
- The service has occasionally frozen under concurrent load and needed a restart. Doesn't happen under light load.
- Memory usage climbs over time and never drops, even when traffic is quiet.
- Latency rises as the number of distinct users grows, even though each user's own history stays small.
- Some inputs cause an unexpected runtime error instead of the documented validation error.
- Requests right at a window boundary sometimes don't match a manual recount.
- Toggling the enable/disable flag while traffic is flowing doesn't always take effect right away.

## Constraints on Tests You Write

- No dependence on real time — no `Thread.sleep`, no system clock reads; use a fixed or injected time source instead.
- No random or non-deterministic data.

## Project Structure

```
rate-limiter/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/example/ratelimiter/
    │   ├── ExampleUsage.java
    │   ├── LoadReproduction.java
    │   ├── FlawedRateLimiter.java
    │   └── RateLimiter.java
    └── test/java/com/example/ratelimiter/
        └── FlawedRateLimiterTest.java
```

## Requirements

- Java 1.8 or higher
- Maven 3.3 or higher

## Build and Run

```bash
# Compile the project
mvn compile

# Run ExampleUsage main class
mvn exec:java -Dexec.mainClass="com.example.ratelimiter.ExampleUsage"

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=FlawedRateLimiterTest

# Run tests with verbose output
mvn test -Dtest=FlawedRateLimiterTest -DforkCount=1 -DreuseForks=false
```

## Reproducing Concurrency Issues Under Load

```bash
mvn -q compile
mvn exec:java -Dexec.mainClass="com.example.ratelimiter.LoadReproduction"
```

This runs many threads hitting a shared rate limiter concurrently and prints raw counts (attempts, allowed, internal counters, per-user tallies). Numbers that don't add up, or a run that hangs, are signal. This is a manual stress tool, not a unit test — it's not run by `mvn test`.

## Docker Usage

### Build and Run with Docker

```bash
# Build the Docker image
docker build -t rate-limiter .

# Run the container (executes ExampleUsage by default)
docker run --rm rate-limiter

# Run with interactive mode
docker run -it --rm rate-limiter

# Run a specific class
docker run --rm rate-limiter java -cp /app/classes com.example.ratelimiter.ExampleUsage
```

### Using Docker Compose

```bash
# Build and run the application
docker-compose up --build

# Run tests in Docker
docker-compose --profile test up rate-limiter-test

# Run in detached mode
docker-compose up -d

# Stop and remove containers
docker-compose down
```

### Development with Docker

```bash
# Build only the application
docker-compose build rate-limiter

# Run tests and view results
docker-compose --profile test run --rm rate-limiter-test

# Access container shell for debugging
docker-compose run --rm rate-limiter sh
```



## Test Categories

### Provided baseline tests (`FlawedRateLimiterTest`)

These pass today and show basic functionality. They're examples, not a full spec — see "Reported Issues" above for what they don't cover.

- `testBasicRateLimiting_Success()`: Sequential single-user rate limiting
- `testDifferentUsers_Success()`: Multiple users with separate limits
- `testTimeWindowReset_Success()`: Window-based reset, using a controllable clock instead of a real wait
- `testDisabledRateLimiter_Success()`: Bypass when disabled

### Flaw-demonstration tests

The `FAILURE CASES` section is intentionally empty. Writing tests there that expose the "Reported Issues" is part of the exercise.

## Submission

Include a short written summary alongside your code and tests, covering:
- which flaws you found and how you confirmed each one,
- the fix you chose for each, and why,
- the complexity analysis.

It's fine for this to evolve from your initial notes as your understanding changes.
