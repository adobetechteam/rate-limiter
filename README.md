# Flawed Rate Limiter Project

This is a Maven Java project that demonstrates a deliberately flawed rate limiter implementation. The rate limiter contains multiple intentional issues across different categories.

With help from AI Agents, the interviewee is expected to:
- Identify and explain each flaw in the implementation. Demonstrate them by writing tests that expose the flaws.
- Propose and implement fixes for each flaw, improving the design and robustness of the rate limiter. Explain what the fixes are. Demonstrate the fixes with passing tests.
- Analyze time and space complexity of the original and fixed implementations.

## Project Structure

```
rate-limiter/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/example/ratelimiter/
    │   ├── ExampleUsage.java
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

### ✅ Success Tests
These tests pass despite the flaws, showing basic functionality:

- `testBasicRateLimiting_Success()`: Sequential single-user rate limiting
- `testDifferentUsers_Success()`: Multiple users with separate limits  
- `testTimeWindowReset_Success()`: Window-based reset functionality
- `testDisabledRateLimiter_Success()`: Bypass when disabled