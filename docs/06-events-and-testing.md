# 06 — Events & Module Boundary Tests

This is where Spring Modulith earns its keep over "three packages in one app."

## 6.1 Wire the event listener

When `ingestion` finishes embedding a report, it should never directly call something inside `mcpserver`. It publishes `FundIngestedEvent` (defined in page 03); `mcpserver` reacts to it.

`mcpserver/FundReportTools.java` — add a listener method (or put it on `IngestedFundsRegistry`, either works — the important part is it's `@ApplicationModuleListener`, not a direct method call from `ingestion`):

```java
@Component
class IngestedFundsRegistry {

    private final Set<String> funds = ConcurrentHashMap.newKeySet();

    @ApplicationModuleListener
    void on(FundIngestedEvent event) {
        funds.add(event.fundName());
    }

    List<String> getAll() { return List.copyOf(funds); }
    void add(String fundName) { funds.add(fundName); }
}
```

`@ApplicationModuleListener` bundles `@Async` + `@Transactional` + `@TransactionalEventListener` and — because `spring-modulith-starter-jdbc` + H2 are on the classpath (page 02) — every published event is durably logged to an **event publication registry** table. If `mcpserver` is down or throws when handling the event, Modulith retries it; nothing is silently dropped. This is the practical payoff of choosing events over direct calls here: `ingestion` doesn't need to know or care whether `mcpserver`'s listener succeeded synchronously.

## 6.2 Verify module boundaries are actually enforced

`src/test/java/com/you/fundapp/ModularityTests.java`
```java
class ModularityTests {

    ApplicationModules modules = ApplicationModules.of(FundAppApplication.class);

    @Test
    void verifyModuleStructure() {
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        new Documenter(modules).writeDocumentation();   // generates PlantUML diagrams under target/spring-modulith-docs
    }
}
```

Run it:
```bash
mvn test -Dtest=ModularityTests
```

Try this experiment once to see the value: temporarily import something from `ingestion.internal.PublicReportFetcher` directly inside `chat` (a nonsensical dependency, but illustrates the point) and re-run the test — `verify()` should fail with an explicit violation message naming the offending package. Revert it. This is the check that keeps the "modular" in modular monolith as the app grows past what any one person can hold in their head.

## 6.3 Module-scoped integration test (optional but useful)

Spring Modulith also gives you `@ApplicationModuleTest`, which boots *only* the module under test plus its declared dependencies — much faster than a full `@SpringBootTest`, and it fails if the module reaches for something outside its declared boundary at runtime, not just at the static-analysis level:

```java
@ApplicationModuleTest
class IngestionModuleTests {

    @Autowired DocumentIngestionService ingestionService;

    @Test
    void ingestingPublishesEvent(PublishedEvents events) {
        // ... ingest a sample PDF ...
        assertThat(events.ofType(FundIngestedEvent.class)).hasSize(1);
    }
}
```

Next: [07 — Docker Deployment (WSL)](07-docker-deployment.md).
