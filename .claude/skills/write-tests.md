---
name: write-tests
description: Use when writing tests for any layer of the MemoryVault project — backend Kotlin, frontend Angular, integration, or test scripts. Ensures tests provide verifiable evidence of correctness with both positive and negative cases.
---

# Write Tests

## Overview

Tests must **prove correctness with visible evidence**. A test suite that silently passes is as useless as no tests — you can't tell what ran, how many passed, or whether both success and failure paths are covered.

## Core Rules

1. **Every public method gets at least one positive AND one negative test**
2. **Test output must show what ran and how many passed** — never swallow output
3. **Run tests and verify output before committing** — don't trust "it compiled"

## Backend (Kotlin / Spring Boot)

### Unit Tests (JUnit 5 + MockK)

Location: `src/test/kotlin/org/sightech/memoryvault/<domain>/service/<Name>ServiceTest.kt`

**Required test categories per method:**

| Category | What to test | Example |
|----------|-------------|---------|
| Happy path | Valid input returns expected result | `login with valid credentials returns token` |
| Invalid input | Bad input throws or returns error | `login with wrong password throws IllegalArgumentException` |
| Missing data | Entity not found | `login with unknown email throws IllegalArgumentException` |
| Edge cases | Empty lists, nulls, boundaries | `findAll returns empty list when no entities exist` |

**Pattern:**

```kotlin
class FooServiceTest {
    private val repository = mockk<FooRepository>()
    private val service = FooService(repository)

    @Test
    fun `findById returns entity when it exists`() {
        val foo = Foo(id = UUID.randomUUID(), name = "test")
        every { repository.findById(foo.id) } returns Optional.of(foo)

        val result = service.findById(foo.id)

        assertEquals(foo, result)
    }

    @Test
    fun `findById returns null when entity does not exist`() {
        every { repository.findById(any()) } returns Optional.empty()

        val result = service.findById(UUID.randomUUID())

        assertNull(result)
    }
}
```

**Run and verify:**
```bash
./gradlew test --tests "*FooServiceTest"
```

Output MUST show individual test names and a summary line like:
```
Test results: SUCCESS (N tests, N passed, 0 failed, 0 skipped)
```

This is configured in `build.gradle.kts` via `testLogging`. If you don't see test names and counts, the logging config is missing.

### Integration Tests (TestContainers)

Location: `src/test/kotlin/org/sightech/memoryvault/<domain>/<Name>IntegrationTest.kt`

**Required test categories:**

| Category | What to test | Example |
|----------|-------------|---------|
| Success path | Valid HTTP request returns 200 + correct body | `POST /api/auth/login returns JWT` |
| Auth failure | Wrong credentials return 401 | `POST /api/auth/login with wrong password returns 401` |
| Not found | Missing resource returns 404 | `GET /api/bookmarks/{nonexistent} returns 404` |
| Validation | Invalid payload returns 400 | `POST /api/auth/login with empty email returns 400` |

**Pattern:**

```kotlin
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FooIntegrationTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("memoryvault_test")
            withUsername("memoryvault")
            withPassword("memoryvault")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @LocalServerPort
    private var port: Int = 0
    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setup() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `valid request returns 200`() {
        webTestClient.get().uri("/api/foo")
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$").isArray
    }

    @Test
    fun `unauthorized request returns 401`() {
        webTestClient.get().uri("/api/foo")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
```

## Frontend (Angular / Vitest)

### Component Tests

Location: `client/src/app/<feature>/<component>.spec.ts`

#### Components with inline templates

Use `TestBed` with `overrideComponent` to inject mock stores/services:

```typescript
beforeEach(async () => {
  vi.clearAllMocks();
  await TestBed.configureTestingModule({
    imports: [MyComponent],
    providers: [provideNoopAnimations()],
  })
    .overrideComponent(MyComponent, {
      set: {
        providers: [
          { provide: MyStore, useValue: mockStore },
        ],
      },
    })
    .compileComponents();

  fixture = TestBed.createComponent(MyComponent);
  component = fixture.componentInstance;
  fixture.detectChanges();
});
```

#### Components with external templateUrl

**Vitest without `@analogjs/vitest-angular` CANNOT resolve external `templateUrl` files.** Do NOT try to create a component fixture — it will fail with `Component is not resolved`.

Instead, test the component's logic directly without the DOM:

```typescript
// DO NOT import the component class — even importing triggers template resolution
describe('MyComponent logic', () => {
  const mockStore = {
    items: signal([]),
    loadItems: vi.fn(),
  };

  // Mirror the component's methods and test them against the mock store
  function ngOnInit() { mockStore.loadItems(); }
  function getTitle(): string {
    const item = mockStore.items().find(i => i.selected);
    return item?.title || 'Default';
  }

  it('should load items on init', () => {
    ngOnInit();
    expect(mockStore.loadItems).toHaveBeenCalled();
  });

  it('should return default when nothing selected', () => {
    expect(getTitle()).toBe('Default');
  });
});
```

### Service Tests

Use `HttpTestingController` to verify HTTP calls:

```typescript
it('should POST to /api/auth/login', () => {
  service.login('test@example.com', 'pass').subscribe();
  const req = httpMock.expectOne('/api/auth/login');
  expect(req.request.method).toBe('POST');
  req.flush({ token: 'jwt', email: 'test@example.com' });
});

it('should handle error responses', () => {
  let errorOccurred = false;
  service.login('test@example.com', 'wrong').subscribe({
    error: () => { errorOccurred = true; },
  });
  const req = httpMock.expectOne('/api/auth/login');
  req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
  expect(errorOccurred).toBe(true);
});
```

### Required test categories (frontend)

| Category | What to test | Example |
|----------|-------------|---------|
| Creation | Component/service instantiates | `should create` |
| Delegation | Methods call through to store/service | `should delegate search to store` |
| Empty state | UI when no data | `should show empty state when no bookmarks` |
| With data | UI when data present | `should render bookmark rows` |
| Error path | Error handling | `should handle login error` |
| Cancellation | User cancels action | `should not add bookmark when dialog cancelled` |

## Test Scripts

Test scripts in `scripts/` must show clear output. **Never grep away test results.**

```bash
# BAD — swallows evidence
./gradlew test --tests "*FooTest" 2>&1 | grep -E "(PASS|FAIL)" || true

# GOOD — full output, you can see what ran
./gradlew test --tests "*FooTest"
```

## Verification Checklist

Before committing tests, confirm:

- [ ] Every public method has at least one positive test (valid input, expected output)
- [ ] Every public method has at least one negative test (invalid input, error, not found)
- [ ] Test output shows individual test names and summary counts
- [ ] Tests pass: run `./gradlew test` or `npm run test` and read the output
- [ ] No external `templateUrl` components tested via `TestBed.createComponent`

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Gradle tests pass silently, no counts shown | Add `testLogging` with events and `afterSuite` summary to `build.gradle.kts` |
| Using `TestBed.createComponent` with external templateUrl in Vitest | Test component logic directly without importing the component class |
| Only testing happy paths | Add at least one negative test per method (invalid input, missing data, auth failure) |
| Test scripts grep away output | Let full output through — evidence matters |
| Assuming "BUILD SUCCESSFUL" means tests ran | Check the summary line for actual test count — `UP-TO-DATE` means nothing was re-run |
