# Spring Boot Backend Guide

**API Responses, Exception Handling and JPA Entities**: a project-starter reference.

---

## Table of Contents

1. [Overview & Scope](#1-overview--scope)
2. [API Response Standard](#2-api-response-standard)
3. [Exception Handling](#3-exception-handling)
4. [JPA Entities](#4-jpa-entities)
5. [Cross-Cutting Conventions](#5-cross-cutting-conventions)
6. [Pitfalls by Priority](#6-pitfalls-by-priority)
7. [Master Checklist](#7-master-checklist)

---

## 1. Overview & Scope

A reference for structuring a Spring Boot REST project consistently: **how every endpoint responds**, **how errors reach the client**, and **how entities are structured**. Copy the patterns into a new project and adapt the names.

This guide is based on the Medium article *"The Best Way to Structure API Responses in Spring Boot"* by Dulanjaya Sandaruwan (Nov 2024), extended with a baseline exception-handling setup and JPA entity conventions. Where a common baseline implementation has weaknesses, they are listed as pitfalls, with the recommended fix beside them.

### Placeholders used in this guide

| Item | Placeholder | Note |
|------|-------------|------|
| Base package | `com.example.app` | Replace with your own. |
| Error enum | `ErrorMessages` | Sketched in [Section 3.1](#31-overview). |
| `User` entity | `User` | Assumed to exist. Referenced by `UserLog` in [Section 4.2](#42-userlog). |

### Suggested package layout

```
com.example.app
├── constants      ErrorMessages
├── controller     REST controllers
├── dto            ApiResponse, ApiError, PageMetadata, *Request, *Response
├── entity         BaseEntity, User, UserLog, ...
├── exception      BaseException + subclasses, ControllerExceptionHandler
├── repository     Spring Data repositories
└── service        Business logic
```

### How the pieces fit

```
Request ─► Controller ─► Service ─► Repository ─► Entity (extends BaseEntity)
                │             │
                │             └─ throws ResourceNotFoundException(ErrorMessages.E002, args)
                │                               │
                ▼                               ▼
      ApiResponse<T> (success)      ControllerExceptionHandler ─► ApiResponse (error)
                │                               │
                └──────────────► Client ◄───────┘
```

---

## 2. API Response Standard

### 2.1 Goals

| Principle | Meaning |
|-----------|---------|
| **Predictable** | Success and error responses share the same top-level shape. |
| **Informative** | Carries the data plus useful context (message, metadata). |
| **Descriptive** | The outcome is obvious without guessing. |
| **Simple** | No extra nesting or fields that nobody uses. |

### 2.2 Response Envelope

All responses are wrapped in one object:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | string | Yes | `"success"` or `"error"`. |
| `message` | string | Yes | Short, human-readable summary of the outcome. |
| `errorCode` | string | Errors only | Stable code for clients (the `ErrorMessages` constant name, e.g. `E005`). |
| `data` | object / array / null | Yes | The payload. `null` for errors. |
| `metadata` | object | No | Extra info such as pagination. Omitted when unused. |
| `errors` | array | No | Field-level details, mainly for validation failures. |
| `path` | string | No | Request path. Useful for debugging; errors only. |
| `timestamp` | string (ISO-8601) | Yes | When the response was generated. |

### 2.3 JSON Examples

**Success: single resource**

```json
{
  "status": "success",
  "message": "User retrieved successfully",
  "data": { "id": 1, "name": "Jane Doe" },
  "timestamp": "2026-09-19T10:15:30Z"
}
```

**Success: paginated list**

```json
{
  "status": "success",
  "message": "Users retrieved successfully",
  "data": [
    { "id": 1, "name": "Jane Doe" },
    { "id": 2, "name": "John Smith" }
  ],
  "metadata": { "currentPage": 0, "pageSize": 10, "totalPages": 10, "totalItems": 100 },
  "timestamp": "2026-09-19T10:15:30Z"
}
```

**Error: not found (`404`)**

```json
{
  "status": "error",
  "message": "User with id 42 not found",
  "errorCode": "E002",
  "data": null,
  "path": "/api/users/42",
  "timestamp": "2026-09-19T10:15:30Z"
}
```

**Error: validation failure (`400`)**

```json
{
  "status": "error",
  "message": "Validation failed",
  "errorCode": "E005",
  "data": null,
  "errors": [
    { "field": "email", "message": "must be a well-formed email address" },
    { "field": "name", "message": "must not be blank" }
  ],
  "path": "/api/users",
  "timestamp": "2026-09-19T10:15:30Z"
}
```

> Error codes come from the `ErrorMessages` enum sketched in [Section 3.1](#31-overview). The codes shown (`E002`, `E005`) are illustrative.

### 2.4 Java Implementation

**`ApiResponse<T>`**: one wrapper for success and error. `@JsonInclude(NON_NULL)` on optional components keeps them out of the JSON when empty, while `data` is always written (as `null` on errors).

```java
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record ApiResponse<T>(
        String status,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode,
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object metadata,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ApiError> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL) String path,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, null, data, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message, T data, Object metadata) {
        return new ApiResponse<>("success", message, null, data, metadata, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String errorCode, String message, String path) {
        return new ApiResponse<>("error", message, errorCode, null, null, null, path, Instant.now());
    }

    public static <T> ApiResponse<T> error(String errorCode, String message,
                                           List<ApiError> errors, String path) {
        return new ApiResponse<>("error", message, errorCode, null, null, errors, path, Instant.now());
    }
}

public record ApiError(String field, String message) {}
```

**`PageMetadata`**: a typed alternative to a raw `Map`, so the pagination shape is the same everywhere.

```java
public record PageMetadata(int currentPage, int pageSize, int totalPages, long totalItems) {
    public static PageMetadata from(Page<?> page) {
        return new PageMetadata(page.getNumber(), page.getSize(),
                                page.getTotalPages(), page.getTotalElements());
    }
}
```

### 2.5 Controllers

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) { this.userService = userService; }

    // Single resource: 200
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully",
                userService.findById(id)));
    }

    // Create: 201 + Location header
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id()))
                .body(ApiResponse.success("User created successfully", created));
    }

    // Paginated list
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<UserResponse> result = userService.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully",
                result.getContent(), PageMetadata.from(result)));
    }
}
```

### 2.6 Pagination Metadata

| Field | Description |
|-------|-------------|
| `currentPage` | Zero-based index of the page returned. |
| `pageSize` | Number of items requested per page. |
| `totalPages` | Total number of pages available. |
| `totalItems` | Total number of records across all pages. |

Keep these names identical across all list endpoints.

### 2.7 HTTP Status Code Guide

The body **complements** the HTTP status, it does not replace it. Always set the correct status on the `ResponseEntity`.

| Scenario | HTTP status | `status` field |
|----------|-------------|----------------|
| Successful read / update | `200 OK` | `success` |
| Resource created | `201 Created` | `success` |
| Deleted, no body | `204 No Content` | (no body) |
| Malformed request / validation error | `400 Bad Request` | `error` |
| Not authenticated | `401 Unauthorized` | `error` |
| Not allowed | `403 Forbidden` | `error` |
| Resource does not exist | `404 Not Found` | `error` |
| Conflict (duplicate, version clash) | `409 Conflict` | `error` |
| Unexpected server failure | `500 Internal Server Error` | `error` |

### 2.8 Design Notes & Trade-offs

The wrapper pattern is popular but not universally agreed on. Readers of the original article raised fair objections:

- **Redundancy with HTTP status.** `status` and `message` can duplicate what the status code already says. Clients should branch on the **HTTP status code**, never on the body's `status` string.
- **Envelope on success is optional.** Some teams return the bare resource on success and use a structured body only for errors. In that case, standardize errors with RFC 9457 `application/problem+json` (`ProblemDetail`, supported by Spring 6 / Boot 3).
- **Contract-first (OpenAPI) projects** may find a generic wrapper awkward, since every schema ends up nested under `data`.

Pick one approach for the whole project and apply it consistently. Consistency matters more than which approach you choose.

---

## 3. Exception Handling

Describes a baseline `exception` package and how to bring it in line with the standard in [Section 2](#2-api-response-standard).

### 3.1 Overview

Errors are thrown as unchecked exceptions and translated into HTTP responses in one place, `ControllerExceptionHandler` (`@RestControllerAdvice`).

Most application exceptions carry an `ErrorMessages` enum constant. The constant supplies the **error code** (`name()`), the **message template**, and the **HTTP status**, so the status is defined once, in the enum, and not at each throw site.

A minimal `ErrorMessages` enum (codes are illustrative, add your own):

```java
package com.example.app.constants;

import org.springframework.http.HttpStatus;

public enum ErrorMessages {
    E001(HttpStatus.NOT_FOUND,    "Resource not found"),
    E002(HttpStatus.NOT_FOUND,    "User with id %s not found"),
    E003(HttpStatus.CONFLICT,     "Resource already exists"),
    E004(HttpStatus.UNAUTHORIZED, "Authentication required"),
    E005(HttpStatus.BAD_REQUEST,  "Validation failed");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorMessages(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getMessage()        { return message; }

    public String format(Object... args) { return String.format(message, args); }
}
```

### 3.2 Package Contents

| File | Type | Purpose |
|------|------|---------|
| `ControllerExceptionHandler` | `@RestControllerAdvice` | Central handler mapping exceptions to HTTP responses. |
| `ErrorResponse` | record | Baseline error body **with** `errorCode`. Used for enum-based exceptions. |
| `ErrorMessage` | record | Baseline error body **without** `errorCode`. Used for generic/framework errors. |
| `ResourceNotFoundException` | exception | A requested resource does not exist. |
| `ResourceAlreadyExistException` | exception | Duplicate resource. Plain message, always `409`. |
| `UnauthorizedException` | exception | Authentication/authorization failure. |
| `ServiceException` | exception | Failure inside the service layer. |
| `AppException` | exception | General application-level error. |

`ResourceNotFoundException`, `UnauthorizedException`, `ServiceException` and `AppException` are structurally identical. Each extends `RuntimeException`, holds an `ErrorMessages errorMessage` plus a `formattedMessage`, and offers two constructors:

```java
public ResourceNotFoundException(ErrorMessages errorMessage)                   // fixed message
public ResourceNotFoundException(ErrorMessages errorMessage, Object... args)   // formatted with args
```

`ResourceAlreadyExistException` is the exception to the pattern: it takes only a `String` and has no `ErrorMessages`.

**Suggested usage:**

| Situation | Throw |
|-----------|-------|
| Entity not found | `ResourceNotFoundException` |
| Duplicate (email, name, ...) | `ResourceAlreadyExistException` |
| Not logged in / not permitted | `UnauthorizedException` |
| Business rule or service failure | `ServiceException` |
| Anything else app-specific | `AppException` |

Because the status comes from the enum constant, the exception class mainly signals intent. An `UnauthorizedException` returns `401` only if its constant is defined with `401`.

### 3.3 Baseline Error Shapes

The baseline returns two different bodies.

**`ErrorResponse`** (enum-based errors)

```java
public record ErrorResponse(int statusCode, Date timestamp, String errorCode,
                            String errorMessage, String description) {}
```

```json
{
  "statusCode": 404,
  "timestamp": "2026-09-19T10:15:30.123+00:00",
  "errorCode": "E001",
  "errorMessage": "Resource not found",
  "description": "uri=/api/resource/42"
}
```

**`ErrorMessage`** (generic errors)

```java
public record ErrorMessage(int statusCode, Date timestamp, String errorMessage, String description) {}
```

```json
{
  "statusCode": 409,
  "timestamp": "2026-09-19T10:15:30.123+00:00",
  "errorMessage": "Resource already exists",
  "description": "uri=/api/resource"
}
```

`description` holds `request.getDescription(false)`, i.e. `uri=<request path>`.

### 3.4 Handler Mapping

| Exception | Handler method | HTTP status | Body | Logged |
|-----------|----------------|-------------|------|--------|
| `ResourceNotFoundException` | `resourceNotFoundException` | from enum | `ErrorResponse` | `error` + stack trace |
| `ResourceAlreadyExistException` | `resourceAlreadyExistException` | `409` | `ErrorMessage` | no |
| `SignatureVerificationException` (webhook signature check, from a payment-provider SDK) | `signatureVerificationException` | `400` | `ErrorMessage` | no |
| `AppException` | `handleAppException` | from enum | `ErrorResponse` | `error` + stack trace |
| `UnauthorizedException` | `handleUnauthorizedException` | from enum | `ErrorResponse` | `error` + stack trace |
| `ServiceException` | `handleServiceException` | from enum | `ErrorResponse` | `error` + stack trace |
| `HttpMessageNotReadableException` | `jsonExceptionHandler` | `400` | `ErrorMessage` | `error` (message only) |
| `ConstraintViolationException` | `handleConstraintsException` | `400` (or `E005`'s status if empty) | `ErrorResponse` (partial) | no |
| `MethodArgumentNotValidException` | `constraintViolationHandler` | `400` | `ErrorMessage` | no |
| `Exception` (fallback) | `globalExceptionHandler` | `500` | `ErrorMessage` | `error` (message only) |

Spring picks the **most specific** matching handler, so `Exception` is the last resort.

### 3.5 How to Throw Exceptions

```java
throw new ResourceNotFoundException(ErrorMessages.E001);            // fixed message
throw new ResourceNotFoundException(ErrorMessages.E002, userId);    // formatted with args
throw new ResourceAlreadyExistException("User with this email already exists");   // always 409
throw new UnauthorizedException(ErrorMessages.E004);
```

Do not build error `ResponseEntity` objects in controllers. Throw and let `ControllerExceptionHandler` respond.

**Adding a new error:** add a constant to `ErrorMessages` with its message and `HttpStatus`, throw the fitting exception with it, and the existing handlers pick up the code and status.

### 3.6 Validation Errors

| Where constraints live | Exception | Baseline handler behaviour |
|------------------------|-----------|----------------------------|
| `@Valid` on a `@RequestBody` DTO | `MethodArgumentNotValidException` | Collects **all** messages into one string like `"[msg1, msg2]"`. |
| `@Validated` class with `@PathVariable` / `@RequestParam` constraints | `ConstraintViolationException` | Uses only the **first** violation and treats its `messageTemplate` as an `ErrorMessages` constant name. |

For the second case, the constraint's `message` must equal an enum constant name:

```java
@GetMapping("/{id}")
public Foo get(@PathVariable @Positive(message = "E005") Long id) { ... }
```

### 3.7 Common Pitfalls (Baseline Handler)

Roughly by priority.

1. **`ConstraintViolationException` can bypass the handler.** `ErrorMessages.valueOf(error)` throws `IllegalArgumentException` when the message template isn't an enum constant name (a default Jakarta message, or a typo). It is thrown inside the handler, so the client won't get the standard body.
2. **Incomplete constraint response.** If the builder never sets `statusCode`, `timestamp` or `description`, the body has `"statusCode": 0` and `null` for the others.
3. **The catch-all handler catches Spring's own exceptions.** `@ExceptionHandler(Exception.class)` also receives unsupported method (`405`), unsupported media type (`415`), missing request parameter, argument type mismatch and unknown routes, returning `500` instead of their natural `4xx`. Spring Security's `AccessDeniedException` can be swallowed the same way if thrown from within a controller call.
4. **Internal details leak.** Returning `ex.getMessage()` from the `Exception` and `HttpMessageNotReadableException` handlers can expose class names or JSON parser internals.
5. **Two body shapes.** Clients must handle `ErrorResponse` (with code) and `ErrorMessage` (without). The cases that matter most for client logic, `409` and validation `400`, lack a code.
6. **Duplicate code.** Four identical handlers and four identical exception classes.
7. **Validation messages are one string**, so clients can't tell which field failed.
8. **Minor:** `java.util.Date` instead of `Instant`; expected `4xx` errors logged at `error` with stack traces (noisy, `warn` is enough); commented-out code left in handlers.

### 3.8 Aligning with the API Response Standard

**Field mapping**

| Baseline field | Standard field | Notes |
|----------------|----------------|-------|
| `statusCode` | HTTP status line | Keep in body only if clients rely on it. |
| `errorMessage` | `message` | |
| `errorCode` | `errorCode` | Kept: stable codes are useful to clients. |
| `description` | `path` | Store just the URI (`request.getRequestURI()`). |
| `timestamp` | `timestamp` | `Date` becomes `Instant`. |
| *(none)* | `status` | Always `"error"`. |
| *(none)* | `errors[]` | Field-level validation errors. |

With this mapping, `ErrorResponse` and `ErrorMessage` are both replaced by the single `ApiResponse` from [Section 2.4](#24-java-implementation).

**Step 1: one base exception.** Replace the four duplicated classes, and move `ResourceAlreadyExistException` onto it so duplicates also get an error code.

```java
@Getter
public abstract class BaseException extends RuntimeException {
    private final ErrorMessages errorMessage;

    protected BaseException(ErrorMessages errorMessage, Object... args) {
        super(args == null || args.length == 0 ? errorMessage.getMessage() : errorMessage.format(args));
        this.errorMessage = errorMessage;
    }
}

public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException(ErrorMessages m, Object... args) { super(m, args); }
}
// UnauthorizedException, ServiceException, AppException, ResourceAlreadyExistException: same pattern
```

**Step 2: one handler for all of them**

```java
@ExceptionHandler(BaseException.class)
public ResponseEntity<ApiResponse<Void>> handleBase(BaseException ex, HttpServletRequest req) {
    ErrorMessages em = ex.getErrorMessage();
    log.warn("{}: {}", em.name(), ex.getMessage());
    return ResponseEntity.status(em.getHttpStatus())
            .body(ApiResponse.error(em.name(), ex.getMessage(), req.getRequestURI()));
}
```

**Step 3: validation with per-field errors**

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException ex,
                                                            HttpServletRequest req) {
    List<ApiError> fields = ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiError(f.getField(), f.getDefaultMessage()))
            .toList();
    return ResponseEntity.badRequest().body(
            ApiResponse.error(ErrorMessages.E005.name(), "Validation failed", fields, req.getRequestURI()));
}
```

**Step 4: a safe constraint-violation handler.** It falls back to `E005` instead of throwing.

```java
@ExceptionHandler(ConstraintViolationException.class)
public ResponseEntity<ApiResponse<Void>> handleConstraints(ConstraintViolationException ex,
                                                            HttpServletRequest req) {
    ErrorMessages em = ex.getConstraintViolations().stream().findFirst()
            .map(ConstraintViolation::getMessageTemplate)
            .flatMap(ControllerExceptionHandler::toErrorMessage)
            .orElse(ErrorMessages.E005);
    return ResponseEntity.badRequest()
            .body(ApiResponse.error(em.name(), em.getMessage(), req.getRequestURI()));
}

private static Optional<ErrorMessages> toErrorMessage(String name) {
    try { return Optional.of(ErrorMessages.valueOf(name)); }
    catch (IllegalArgumentException e) { return Optional.empty(); }
}
```

**Step 5: a catch-all that never exposes internals**

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
    log.error("Unexpected error on {}", req.getRequestURI(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred", req.getRequestURI()));
}
```

To stop the catch-all from turning Spring's own `4xx` errors into `500`, either extend `ResponseEntityExceptionHandler` and override its handlers, or add explicit handlers for `HttpRequestMethodNotSupportedException` (405), `HttpMediaTypeNotSupportedException` (415), `MissingServletRequestParameterException` (400) and `MethodArgumentTypeMismatchException` (400).

---

## 4. JPA Entities

### 4.1 BaseEntity

`com.example.app.entity.BaseEntity` is a `@MappedSuperclass`: it is **not** a table itself. Its fields and callbacks are copied into the table of every entity that extends it.

| Aspect | Detail |
|--------|--------|
| Annotations | `@MappedSuperclass`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` |
| Implements | `Serializable` |
| Provides | Generated ID, `createdOn` / `updatedOn`, `isActive` / `isDeleted` flags, `isNew()` helper |

#### Fields

| Field | Column | Java type | Set by | JSON |
|-------|--------|-----------|--------|------|
| `id` | `ID` | `Long` | Database (`IDENTITY`) | `id` |
| `createdOn` | `CREATED_ON` | `LocalDateTime` | `@PrePersist` | `created_on` (`dd-MM-yyyy HH:mm:ss`) |
| `updatedOn` | `UPDATED_ON` | `LocalDateTime` | `@PrePersist`, `@PreUpdate` | `updated_on` (`dd-MM-yyyy HH:mm:ss`) |
| `isActive` | `IS_ACTIVE` | `boolean` | `@PrePersist` (`true`) | hidden (`@JsonIgnore`) |
| `isDeleted` | `IS_DELETED` | `boolean` | `@PrePersist` (`false`) | hidden (`@JsonIgnore`) |

All five columns are non-null. `isNew()` is `@JsonIgnore`d and simply checks `id == null`. `BaseEntity` does not implement Spring Data's `Persistable`, so it is a convenience helper only.

#### Lifecycle callbacks

```java
@PrePersist
protected void onCreate() {
    isActive = true;
    isDeleted = false;
    createdOn = LocalDateTime.now();
    updatedOn = LocalDateTime.now();
}

@PreUpdate
protected void onUpdate() {
    updatedOn = LocalDateTime.now();
}
```

- `onCreate()` **always** overwrites `isActive` and `isDeleted`. A new entity cannot be saved as inactive by setting the flag before `save()`.
- Callbacks run only when JPA/Hibernate manages the change. Bulk JPQL/HQL `UPDATE` and native SQL **skip them**, so `updatedOn` won't change.
- A subclass that declares its own callback with a **different name** runs it in addition. If it overrides `onCreate()` / `onUpdate()`, it must call `super`.

#### Constructors

| Constructor | Source | Use |
|-------------|--------|-----|
| `BaseEntity()` | `@NoArgsConstructor` | Required by JPA. |
| `BaseEntity(Long id, LocalDateTime createdOn, LocalDateTime updatedOn, boolean isActive, boolean isDeleted)` | `@AllArgsConstructor` | Rarely needed directly. |
| `BaseEntity(Long id)` | Hand-written | Lightweight reference to an existing row (e.g. to set a foreign key). Constructors aren't inherited, so a subclass needs its own `(Long id)` constructor calling `super(id)`. |

#### Extending

```java
@Entity
@Table(name = "INVOICE")
@Getter
@Setter
public class Invoice extends BaseEntity {

    @Column(name = "INVOICE_NUMBER", nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "AMOUNT", nullable = false)
    private BigDecimal amount;
}
```

#### Soft delete

`isDeleted` marks a row as deleted without removing it. **Nothing in `BaseEntity` filters those rows automatically.** Every query must exclude them, or the entity needs a filter:

```java
// Option A: derived query
List<Invoice> findByIsDeletedFalse();

// Option B: filter on the entity (Hibernate 6.3+)
@Entity
@SQLRestriction("IS_DELETED = false")
public class Invoice extends BaseEntity { ... }
```

```java
invoice.setDeleted(true);        // Lombok setter for a boolean field named "isDeleted"
invoiceRepository.save(invoice);
```

Use `isActive` for reversible business states (enabled/disabled) and `isDeleted` for removal.

#### Things to watch out for

1. **`@Data` on an entity.** It builds `equals()` / `hashCode()` from **all** fields, including the mutable `id`, so the hash changes once the entity is saved, which can break `HashSet` / `HashMap` use. Subclasses using `@Data` ignore parent fields in `equals()` unless `@EqualsAndHashCode(callSuper = true)` is added. Prefer `@Getter` / `@Setter` with an ID-based `equals` / `hashCode`.
2. **`@JsonIgnore` on `isActive` / `isDeleted` may not hide them.** For a `boolean` field named `isActive`, Lombok generates `isActive()` and `setActive()`. Jackson derives the property name `active` from the getter, while the annotated field is named `isActive`, so the getter can still be serialized as `"active"` / `"deleted"`. Verify with a serialization test. If they show up, put `@JsonIgnore` on the getter, or rename the fields to `active` / `deleted` and keep `@Column(name = "IS_ACTIVE")`.
3. **The `timezone` in `@JsonFormat` does nothing here.** `LocalDateTime` has no zone, so `timezone = "GMT+5:30"` converts nothing. The output is whatever `LocalDateTime.now()` returned, in the **server's default zone**. Set the JVM zone (`-Duser.timezone=Asia/Kolkata`) or use `Instant` / `OffsetDateTime`.
4. **Entities as API output.** Returning entities inside `ApiResponse.data` leaks the database shape into the API contract. Prefer response DTOs.
5. **`IDENTITY` disables JDBC insert batching.** For bulk inserts, a sequence-based strategy is faster if the database supports it.
6. **`LocalDateTime` for audit data.** It has no zone and shifts with daylight saving. Storing UTC (`Instant`) is safer for financial or audit data where timestamps matter.

### 4.2 UserLog

`com.example.app.entity.UserLog` (table `user_log`) records user actions: who did it (`user`), what (`action`) and a finer detail (`subAction`). It extends `BaseEntity`.

#### Table mapping

| Column | Source | Type | Nullable | Notes |
|--------|--------|------|----------|-------|
| `ID`, `CREATED_ON`, `UPDATED_ON`, `IS_ACTIVE`, `IS_DELETED` | `BaseEntity` | see 4.1 | No | Inherited. |
| `USER_ID` | `user` | FK to `User.ID` | No | `@ManyToOne(optional = false)` |
| `ACTION` | `action` | `String` | Yes | Main action name. |
| `SUB_ACTION` | `subAction` | `String` | Yes | More specific detail. |

`userName` and `type` are `@Transient` and have **no column**.

#### Fields

| Field | Java type | JSON name | Persisted | Description |
|-------|-----------|-----------|-----------|-------------|
| `user` | `User` | hidden | Yes, as `USER_ID` | The user who performed the action. |
| `action` | `String` | `action` | Yes | What happened. |
| `subAction` | `String` | `sub_action` | Yes | Extra detail. |
| `userName` | `String` | `user_name` | No | Display name, filled at runtime. |
| `type` | `String` | `user_type` | No | User type/role, filled at runtime. |

Getters and setters are written by hand (no Lombok).

#### Relationship with User

```java
@JoinColumn(name = "USER_ID", referencedColumnName = "ID")
@ManyToOne(optional = false)
@JsonIgnore
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
private User user;
```

| Annotation | Effect |
|------------|--------|
| `@ManyToOne(optional = false)` | Many logs per user; a log **must** have a user. |
| `@JoinColumn(...)` | Foreign key `USER_ID` points to the user's `ID`. |
| `@JsonIgnore` | The `User` object is left out of JSON, on output **and** input. A client can't choose the user via the request body, so the server must set it (e.g. from the authenticated principal). |
| `@JsonIgnoreProperties({...})` | Would hide Hibernate proxy fields if the user were serialized. No visible effect while `@JsonIgnore` is present. |

#### Transient fields

`userName` and `type` exist only so the JSON can show who performed the action, since `user` is hidden. JPA never reads or writes them, and **they are `null` unless your code fills them**:

```java
logs.forEach(log -> {
    log.setUserName(log.getUser().getName());   // adjust to the real getters on User
    log.setType(log.getUser().getType());
});
```

#### JSON

```json
{
  "id": 501,
  "created_on": "19-09-2026 15:45:10",
  "updated_on": "19-09-2026 15:45:10",
  "action": "LOGIN",
  "sub_action": "SUCCESS",
  "user_name": "Jane Doe",
  "user_type": "ADMIN"
}
```

`id`, `created_on` and `updated_on` come from `BaseEntity` (formats as configured in Section 4.1). The `action` values are illustrative. Inside the standard envelope this object sits in `data`.

#### Usage

```java
UserLog log = new UserLog();
log.setUser(user);
log.setAction("LOGIN");
log.setSubAction("SUCCESS");
userLogRepository.save(log);            // repository name is an example
```

**Recommended: return a DTO** built directly in the query. This avoids the transient fields and avoids loading the whole `User`:

```java
public record UserLogResponse(Long id, LocalDateTime createdOn, String action,
                              String subAction, String userName, String userType) {}

@Query("""
       select new com.example.app.dto.UserLogResponse(
           l.id, l.createdOn, l.action, l.subAction, u.name, u.type)
       from UserLog l join l.user u
       where u.id = :userId
       order by l.createdOn desc
       """)
Page<UserLogResponse> findResponsesByUserId(@Param("userId") Long userId, Pageable pageable);
```

`u.name` and `u.type` are assumptions. Use the real property names of your `User` entity.

#### Things to watch out for

1. **`@ManyToOne` is `EAGER` by default.** Each loaded `UserLog` also loads its `User`, and listing many logs can trigger one extra query per user (N+1). `fetch = FetchType.LAZY` is usually better for a log table.
2. **Transient fields are easy to forget.** Any code path that skips `setUserName` / `setType` returns `null` for `user_name` and `user_type`. A DTO removes the risk.
3. **Naming mismatch.** The Java field `type` becomes `user_type` in JSON. Renaming it to `userType` makes code and API match.
4. **Free-text `action` / `subAction`** allow typos and inconsistent values (`"login"`, `"LOGIN"`). Use constants or an enum with `@Enumerated(EnumType.STRING)`.
5. **Log-table performance.** Logs grow fast. Index `(USER_ID, CREATED_ON)` and consider a retention or archive policy.
6. **Inherited fields that don't fit a log.** Entries are written once and rarely changed, so `updatedOn`, `isActive` and `isDeleted` add little. If soft delete is used, it still doesn't filter rows by itself (see [Soft delete](#soft-delete)).
7. **Sensitive data.** Don't put passwords, tokens or full request payloads in `subAction`. Logs tend to be widely readable and long-lived.

---

## 5. Cross-Cutting Conventions

The baseline components above use different conventions from each other and from the response standard. Decide each once for the whole API.

| Topic | Baseline | Recommendation |
|-------|---------------|----------------|
| **JSON property names** | Entities use `snake_case` (`created_on`, `sub_action`); the article and this guide's envelope use `camelCase`. | Pick one. To apply `snake_case` globally, set `spring.jackson.property-naming-strategy=SNAKE_CASE` instead of per-field `@JsonProperty`. |
| **Timestamp format** | Entities: `dd-MM-yyyy HH:mm:ss`. Errors: `Date`. Envelope: ISO-8601. | ISO-8601 everywhere, using `Instant` (UTC). |
| **Time zone** | `LocalDateTime.now()` in the server's zone; `@JsonFormat(timezone)` has no effect. | Store UTC, or set the JVM zone explicitly. |
| **Error body** | Two shapes (`ErrorResponse`, `ErrorMessage`). | One shape: the `ApiResponse` envelope ([Section 3.8](#38-aligning-with-the-api-response-standard)). |
| **What controllers return** | Entities with `@JsonIgnore` / `@Transient` workarounds. | DTOs / records, mapped from entities. |
| **Status source of truth** | Body `status` plus HTTP status. | HTTP status is authoritative; body `status` is informational. |
| **Soft delete** | Flags exist, no automatic filtering. | `@SQLRestriction` on entities or explicit query filters. |

---

## 6. Pitfalls by Priority

Use this list when starting a project (build these in from day one) or when reviewing an existing one.

**High: fix first**

| # | Issue | Where |
|---|-------|-------|
| 1 | `ConstraintViolationException` handler can throw (`valueOf`) and returns `statusCode: 0` | [3.7](#37-common-pitfalls-baseline-handler), fix in [3.8](#38-aligning-with-the-api-response-standard) step 4 |
| 2 | Catch-all handler returns `ex.getMessage()` and turns Spring's `4xx` into `500` | 3.7, fix in 3.8 step 5 |
| 3 | Confirm `active` / `deleted` are not exposed in JSON | [4.1](#41-baseentity), item 2 |
| 4 | `UserLog.userName` / `type` can silently be `null` | 4.2, item 2 (use a DTO) |

**Medium**

| # | Issue | Where |
|---|-------|-------|
| 5 | Merge `ErrorResponse` / `ErrorMessage` into the single envelope | 3.8 |
| 6 | Collapse four duplicate exceptions and handlers into `BaseException` | 3.8 steps 1-2 |
| 7 | `UserLog.user` is `EAGER` (N+1) | 4.2, item 1 |
| 8 | Timestamps in server zone; `timezone` attribute has no effect | 4.1, item 3 |
| 9 | Soft-deleted rows are not filtered automatically | 4.1 soft delete |
| 10 | `@Data` on entities affects `equals` / `hashCode` | 4.1, item 1 |
| 11 | Validation errors returned as one string | 3.8 step 3 |

**Low**

| # | Issue | Where |
|---|-------|-------|
| 12 | `Date` to `Instant` | 3.7, item 8 |
| 13 | Expected `4xx` errors logged at `error` with stack traces | 3.7, item 8 |
| 14 | Free-text `action` / `subAction` | 4.2, item 4 |
| 15 | Index on `user_log (USER_ID, CREATED_ON)` | 4.2, item 5 |
| 16 | Mixed `snake_case` / `camelCase` | Section 5 |
| 17 | Remove commented-out code from handlers | 3.7, item 8 |

---

## 7. Master Checklist

**API responses**
- [ ] Returns `ResponseEntity<ApiResponse<T>>` with the correct HTTP status.
- [ ] Success message is short and meaningful.
- [ ] List endpoints include pagination `metadata` with standard field names.
- [ ] Controllers return DTOs, not raw entities.
- [ ] Response examples are documented in Swagger / OpenAPI.

**Exceptions**
- [ ] Errors are thrown as exceptions, never hand-built in controllers.
- [ ] Every enum-based error has an `ErrorMessages` constant with the right `HttpStatus`.
- [ ] Constraint `message` values on path/query parameters match an `ErrorMessages` constant name.
- [ ] All error responses use one body shape, with an `errorCode`.
- [ ] The catch-all handler returns a generic message, not `ex.getMessage()`.
- [ ] Expected client errors (`4xx`) are logged at `warn`, unexpected ones (`5xx`) at `error` with the stack trace.
- [ ] Spring's built-in `4xx` exceptions are not swallowed by the catch-all.

**Entities**
- [ ] New entities extend `BaseEntity`.
- [ ] No entity sets its own `id`, `createdOn` or `updatedOn` manually.
- [ ] Queries exclude soft-deleted rows (`IS_DELETED = false`).
- [ ] Bulk `UPDATE` queries set `UPDATED_ON` explicitly.
- [ ] Subclass overrides of `onCreate()` / `onUpdate()` call `super`.
- [ ] A serialization test confirms `active` / `deleted` are not exposed.
- [ ] The server time zone matches the expected timestamp zone.

**Activity logs (`UserLog`)**
- [ ] Every log is saved with a non-null `user`, set server-side.
- [ ] `userName` / `type` are filled in, or a DTO is used instead.
- [ ] `action` / `subAction` come from constants or an enum.
- [ ] Log queries are paginated and ordered by `createdOn`.
- [ ] An index exists on `USER_ID` (and `CREATED_ON`).
- [ ] No sensitive data is written into `action` or `subAction`.
