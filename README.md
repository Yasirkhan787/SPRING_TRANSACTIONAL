# Spring Boot `@Transactional` Lab — Propagation & Isolation by Example

A small, hands-on Spring Boot project for **learning how database transactions really behave**.
Instead of reading theory only, you trigger REST endpoints and watch the console to *see*:

- what happens to your data when an exception is thrown in the middle of a money transfer (**propagation**), and
- how two concurrent transactions affect each other: dirty reads, non-repeatable reads, phantom reads (**isolation levels**).

> **Stack:** Java 17 · Spring Boot 4.1.1 · Spring Data JPA / Hibernate · MySQL 8 (InnoDB) · Lombok · Maven

---

## Table of Contents

1. [Transaction concepts in 5 minutes](#1-transaction-concepts-in-5-minutes)
2. [Project structure](#2-project-structure)
3. [Domain model](#3-domain-model)
4. [How the code maps to the concepts](#4-how-the-code-maps-to-the-concepts)
5. [Prerequisites](#5-prerequisites)
6. [Setup & run](#6-setup--run)
7. [API reference](#7-api-reference)
8. [Lab 1 — Propagation experiments](#8-lab-1--propagation-experiments)
9. [Lab 2 — Isolation experiments](#9-lab-2--isolation-experiments)
10. [Reading the transaction logs](#10-reading-the-transaction-logs)
11. [Running the automated test](#11-running-the-automated-test)
12. [Known issues & gotchas in this code](#12-known-issues--gotchas-in-this-code)
13. [Ideas to extend the lab](#13-ideas-to-extend-the-lab)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. Transaction concepts in 5 minutes

### What is a transaction?
A group of database operations that behave as **one unit**: either *all* succeed (commit) or *none* do (rollback).
Classic example: a bank transfer = *debit sender* + *credit receiver*. If the credit fails, the debit must be undone.

### ACID
| Letter | Meaning | Where you see it in this project |
|---|---|---|
| **A**tomicity | All or nothing | Wallet transfer rolls back when `credit()` throws |
| **C**onsistency | DB moves from one valid state to another | Money is never "lost" between two accounts |
| **I**solation | Concurrent transactions don't corrupt each other | The whole `/isolation/*` lab |
| **D**urability | Committed data survives crashes | MySQL/InnoDB guarantees it |

### How `@Transactional` works
Spring wraps your bean in a **proxy**. When a call comes in through the proxy, Spring:

```
caller ──► [Proxy] ──► begin tx ──► your method ──► commit (or rollback on exception)
```

Two consequences you must remember:

1. **Self-invocation is NOT transactional.** `this.otherMethod()` bypasses the proxy, so the annotation on `otherMethod` is ignored.
   That is exactly why `WalletService.transfer()` calls `debit()`/`credit()` on a **different bean** (`UserService`).
2. **Transactions are bound to a thread.** Every thread gets its own transaction/connection.
   That is how this lab simulates "two users at once" — it simply starts two `Thread`s.

### Rollback rules (default)
- `RuntimeException` / `Error` → **rollback**
- Checked exceptions (e.g. `IOException`) → **commit** (unless you set `rollbackFor`)
- You can also force it: `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` (used in the Read-Uncommitted demo).

### Propagation — "what if a transaction already exists when I'm called?"

| Propagation | Existing tx? | Behaviour | Typical use |
|---|---|---|---|
| `REQUIRED` *(default)* | yes → join it / no → create one | One shared tx | Normal service methods |
| `REQUIRES_NEW` | **suspends** the current tx and always starts a **new, independent** one | Commits/rolls back on its own | Audit logs that must be saved even if the main tx fails |
| `NOT_SUPPORTED` | suspends the current tx and runs **without** any tx | No tx at all | Long reads/reports that shouldn't hold a tx |
| `SUPPORTS` | joins if present, otherwise runs without tx | Flexible | Read helpers |
| `MANDATORY` | joins if present, **throws** if none | Enforce "must be called inside a tx" | Internal helpers |
| `NEVER` | **throws** if a tx exists | Enforce "must NOT be in a tx" | Rare |
| `NESTED` | creates a **savepoint** inside the current tx | Partial rollback to savepoint | Optional sub-steps |

### Isolation — "how much can I see of other transactions' work?"

| Level | Dirty read | Non-repeatable read | Phantom read |
|---|:---:|:---:|:---:|
| `READ_UNCOMMITTED` | possible | possible | possible |
| `READ_COMMITTED` | prevented | possible | possible |
| `REPEATABLE_READ` | prevented | prevented | possible *(standard)* |
| `SERIALIZABLE` | prevented | prevented | prevented |

- **Dirty read** – you read data another transaction has *not committed* (and may still roll back).
- **Non-repeatable read** – you read the *same row* twice in one tx and get *different values* (someone committed an UPDATE in between).
- **Phantom read** – you run the *same query* twice and get a *different set of rows* (someone committed an INSERT/DELETE in between).

> ⚠️ **MySQL/InnoDB note.** The table above is the SQL *standard*. InnoDB's `REPEATABLE_READ` (the MySQL default) uses MVCC snapshots and
> **also hides phantoms for normal SELECTs**. That is why this project deliberately uses `READ_COMMITTED` to *demonstrate* a phantom read (see [Lab 2](#9-lab-2--isolation-experiments)).
> `Isolation.DEFAULT` means "use the database's default", which is `REPEATABLE_READ` on MySQL.

---

## 2. Project structure

```
src/main/java/com/yasirkhan/transactional
├── TransactionalApplication.java          # Spring Boot entry point
├── configs/
│   └── TransactionManagerConfig.java      # Custom JpaTransactionManager that logs begin/commit/rollback
├── controllers/
│   ├── UserController.java                # POST /users, GET /users           (seed + inspect data)
│   ├── WalletController.java              # POST /wallet/transfer             (PROPAGATION lab)
│   └── IsolationController.java           # GET /isolation/*                  (ISOLATION lab)
├── services/
│   ├── UserService.java                   # addUser, getAllUsers, debit, credit (propagation playground)
│   ├── WalletService.java                 # transfer() = the outer @Transactional method
│   ├── UserServiceForReadUnCommitted.java # @Transactional(isolation = READ_UNCOMMITTED)
│   ├── UserServiceForReadCommitted.java   # @Transactional(isolation = READ_COMMITTED)
│   ├── UserServiceForRepeatableRead.java  # @Transactional(isolation = REPEATABLE_READ) (+ phantom problem)
│   ├── UserServiceForSerializable.java    # @Transactional(isolation = SERIALIZABLE)    (+ phantom solution)
│   └── isolations/                        # "Scenario runners": start Thread A and Thread B, then join()
│       ├── ReadUnCommited.java
│       ├── ReadCommited.java
│       ├── RepeatableRead.java
│       └── SerializableRead.java
├── repositories/UserRepository.java       # findByBalanceGreaterThan + a PESSIMISTIC_WRITE lock query
├── entities/                              # User (1) ──< (N) UserContact
├── dtos/                                  # UserRequest, UserResponse, ErrorResponse (Java records)
└── exceptions/GlobalExceptionHandler.java # NoSuchElement → 404, everything else → 500
```

There are two cleanly separated layers for the isolation lab:

- **`services/UserServiceFor<Level>`** – the *transactional* code (what Transaction A / B actually do).
- **`services/isolations/<Level>`** – the *orchestration*: spawns two threads with sleeps so the transactions overlap in a predictable way.

---

## 3. Domain model

```
users                          user_contacts
-----                          -------------
id (PK, auto-increment)  ◄──┐  id (PK)
name                        └─ user_id (FK)
balance (double)               phone_number (NOT NULL)
```

- `User` → `@OneToMany(mappedBy = "user", cascade = ALL, orphanRemoval = true)` (inverse side, no FK column).
- `UserContact` → `@ManyToOne @JoinColumn(name = "user_id")` (owning side, holds the FK) and `@JsonIgnore` on `user` to avoid infinite JSON recursion.
- Both entities have detailed Javadoc comments explaining `mappedBy`, `cascade`, and `orphanRemoval`.

Tables are created automatically (`spring.jpa.hibernate.ddl-auto=update`).

---

## 4. How the code maps to the concepts

### Propagation lab: `WalletService` + `UserService`

```java
// WalletService  (outer transaction T1, propagation REQUIRED by default)
@Transactional
public void transfer(Long senderId, Long receiverId, double amount) {
    userService.debit(senderId, amount);      // step 1
    userService.credit(receiverId, amount);   // step 2  (throws ArithmeticException on purpose: 1/0)
}
```

`UserService.debit()` and `UserService.credit()` have these annotations **commented out** so you can switch them on one by one:

```java
// @Transactional(propagation = Propagation.NOT_SUPPORTED)  → above debit()
// @Transactional(propagation = Propagation.REQUIRES_NEW)   → above credit()
```

`credit()` contains `var r = 1/0;` — a deliberate failure **after** the balance was changed, so you can observe exactly what gets rolled back and what doesn't.

### Isolation lab: Transaction A vs Transaction B

Every scenario is a two-thread race:

- **Thread A** = a *writer*: changes data, sleeps 5 s **inside** the transaction (keeps it open), then commits or rolls back.
- **Thread B** = a *reader*: starts a bit later (0.5–2 s) and reads the same data.

`Thread.sleep()` inside a transaction is only for demonstration — never do this in production code.

**Why `entityManager.clear()` appears in the reader code:** Hibernate keeps a first-level cache per transaction. A second `findById()` would be served from memory and
never touch the database, hiding the anomaly. `clear()` forces a real second `SELECT`.

### Custom transaction manager: `TransactionManagerConfig`
Overrides `doBegin`, `doCommit`, `doRollback` of `JpaTransactionManager` to print `Transaction Logger: ...` lines.
It lets you **see** how many *real* transactions were opened for one HTTP request, which is the key to understanding propagation.

---

## 5. Prerequisites

| Tool | Version |
|---|---|
| JDK | 17 or newer |
| MySQL | 8.x (InnoDB) – local install **or** Docker |
| Maven | not needed — the project ships with the Maven Wrapper (`mvnw`) |
| curl / Postman | to call the endpoints |

---

## 6. Setup & run

### 6.1 Start MySQL

**Option A – Docker (quickest, matches `application.properties`):**
```bash
docker run --name mysql-tx \
  -e MYSQL_ROOT_PASSWORD=rootPassword \
  -e MYSQL_DATABASE=transaction \
  -p 3306:3306 -d mysql:8
```

**Option B – existing MySQL:**
```sql
CREATE DATABASE `transaction`;
```
The JDBC URL has no `createDatabaseIfNotExist=true`, so **the database must exist before the app starts.**

### 6.2 Check `src/main/resources/application.properties`

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/transaction?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=rootPassword      # change to your own
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

> The H2 block in that file is commented out. H2 is on the classpath but **not used** — the active database is MySQL
> (isolation behaviour is database-specific, and the lab is written around InnoDB).

### 6.3 Run the application

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

The app starts on **http://localhost:8080**. Hibernate creates the `users` and `user_contacts` tables on first start.

### 6.4 Seed the data (required)

The isolation endpoints are hard-wired to **user id `2`**, and the wallet needs a sender and receiver, so create two users:

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","balance":1000,"contacts":["03001234567"]}'

curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Bob","balance":1000,"contacts":["03007654321"]}'

curl http://localhost:8080/users        # verify: Alice = id 1, Bob = id 2, both 1000.0
```

---

## 7. API reference

| Method | URL | Purpose |
|---|---|---|
| `POST` | `/users` | Create a user with contacts |
| `GET` | `/users` | List users (check balances after each experiment) |
| `POST` | `/wallet/transfer?senderId=1&receiverId=2&amount=100` | Propagation lab |
| `GET` | `/isolation/readUnCommit` | Dirty read |
| `GET` | `/isolation/readCommit` | Dirty read prevented, **non-repeatable read** shown |
| `GET` | `/isolation/repeatableRead` | Non-repeatable read prevented |
| `GET` | `/isolation/serializable` | Reader is blocked until the writer commits |
| `GET` | `/isolation/phantomProblem` | **Phantom read** shown |
| `GET` | `/isolation/phantomSolution` | Phantom read prevented |

Error format (from `GlobalExceptionHandler`):
```json
{ "statusCode": 500, "message": "/ by zero", "timestamp": "2026-..." }
```
`NoSuchElementException` (e.g. unknown user id) → `404`; anything else → `500`.

---

## 8. Lab 1 — Propagation experiments

**Goal:** call the same transfer and watch how different propagation settings change the final balances.

**Before every run reset the data** (see [Reset SQL](#reset-sql)) and keep Alice (id 1) = 1000, Bob (id 2) = 1000.

```bash
curl -X POST "http://localhost:8080/wallet/transfer?senderId=1&receiverId=2&amount=100"
curl http://localhost:8080/users
```

Edit `UserService.java`, restart the app after each change, and compare.

| # | `debit()` | `credit()` | Expected HTTP | Alice (sender) | Bob (receiver) | Why |
|---|---|---|:---:|:---:|:---:|---|
| **A** | *(none)* | *(none)* — **current code** | 500 `/ by zero` | **1000** | 1000 | Both methods **join** the single transaction opened by `transfer()`. The exception rolls back everything. ✅ Atomic. |
| **B** | *(none)* | `REQUIRES_NEW` | 500 `/ by zero` | **1000** | 1000 | `credit` runs in its own tx T2 → T2 rolls back; the exception then bubbles to `transfer()` → T1 (with the debit) rolls back too. Logs show **two** transactions. |
| **C** | `NOT_SUPPORTED` | `REQUIRES_NEW` | 500 | **900** ⚠️ | 1000 | `debit` runs **outside** any tx. Each repository call (`save`) commits by itself, so the debit is **permanent**. The failure afterwards cannot undo it. **Money is lost.** |
| **D** | `REQUIRES_NEW` | `REQUIRES_NEW` | 500 `/ by zero` | **900** ⚠️ | 1000 | `debit` commits in its own independent tx T2. `credit` fails in T3. `transfer()`'s own tx T1 has nothing left to roll back. **Money is lost.** |
| **E** | *(none)* | *(none)* **and remove the `1/0` lines** | 200 | **900** | **1100** | Happy path: one transaction, one commit. |

> **Scenario C detail:** with `NOT_SUPPORTED` there is no transaction, so the explicit `entityManager.flush()` inside `debit()` is expected to fail with
> `TransactionRequiredException` ("No EntityManager with actual transaction available…") *after* the `save()` already committed.
> If you delete that `flush()` line the debit finishes normally and `credit` fails instead — the **final balances are the same** (Alice 900, Bob 1000).
>
> *These outcomes come from reading the code and Spring's documented behaviour — confirm them on your machine with the logs described in [section 10](#10-reading-the-transaction-logs).*

**The lesson:** `REQUIRED` (join) gives you atomicity across several steps. `REQUIRES_NEW` / `NOT_SUPPORTED` deliberately *break* that atomicity — use them only for work that must succeed or fail independently (audit trail, notification outbox, etc.), never for the two halves of a transfer.

### Reset SQL

Run in a MySQL client (`docker exec -it mysql-tx mysql -uroot -prootPassword transaction`):

```sql
UPDATE users SET balance = 1000 WHERE id IN (1, 2);
DELETE FROM user_contacts WHERE user_id > 2;   -- remove users created by the phantom demo
DELETE FROM users WHERE id > 2;
```

---

## 9. Lab 2 — Isolation experiments

Reset the data first (user 2 must have balance `1000`). Each experiment takes ~5–8 seconds because of the deliberate sleeps.
Watch the **application console**, not just the HTTP response.

> ⚠️ `readCommit`, `repeatableRead` and `serializable` **commit** a `-500` debit. Reset the balance between runs.
> `readUnCommit` rolls back, so it leaves data unchanged.

### 9.1 `GET /isolation/readUnCommit` — Dirty Read

```
t=0s   A (READ_UNCOMMITTED): UPDATE balance 1000 → 500   (not committed)
t=2s   B (READ_UNCOMMITTED): SELECT balance  → 500        ← DIRTY READ
t=5s   A: marks rollback-only → ROLLBACK (balance is 1000 again)
```
Console: `Transaction B: Read balance as 500.0` — B saw a value that **never officially existed**.
Check `GET /users`: Bob is still `1000`.

### 9.2 `GET /isolation/readCommit` — Dirty read fixed, Non-Repeatable Read appears

```
t=0s   A (READ_COMMITTED): UPDATE 1000 → 500 (uncommitted)
t=2s   B: first read  → 1000        (A's change is invisible: no dirty read ✅)
t=5s   A: COMMIT
t=6s   B: clear cache, second read → 500   ← NON-REPEATABLE READ
```
Console: `First Read balance as 1000.0` then `Second Read balance as 500.0`.

### 9.3 `GET /isolation/repeatableRead` — Non-Repeatable Read fixed

Same timeline as above, but B uses `REPEATABLE_READ`:

```
t=2s   B: first read  → 1000   (B's snapshot is taken here)
t=5s   A: COMMIT (balance is now 500 in the DB)
t=6s   B: second read → 1000   ← same value, snapshot is stable ✅
```

### 9.4 `GET /isolation/phantomProblem` — Phantom Read

Transaction B runs `SELECT ... WHERE balance > 500` twice, 2 s apart. Meanwhile Transaction A inserts a new user with balance 1000 and commits.

```
t=0s    B (READ_COMMITTED): query → 2 users
t=0.5s  A: INSERT new user (balance 1000), COMMIT
t=2s    B: clear cache, same query → 3 users   ← PHANTOM ROW
```
`UserServiceForRepeatableRead.getUserByBalanceGreaterThan` intentionally uses `READ_COMMITTED` — see the Javadoc there: InnoDB's `REPEATABLE_READ` would hide the phantom.
**Each run inserts a permanent user** → use the [Reset SQL](#reset-sql).

### 9.5 `GET /isolation/phantomSolution` — Phantom Read prevented

Same race, but both transactions are `SERIALIZABLE`. InnoDB turns B's plain `SELECT` into a locking read that locks the scanned range (including gaps), so A's `INSERT` must **wait**:

```
t=0s    B (SERIALIZABLE): query → 2 users   (range lock acquired)
t=0.5s  A: INSERT … ⏳ blocked by B's lock
t=2s    B: same query → 2 users ✅ ; B commits, lock released
t=2s+   A: INSERT proceeds and commits
```

### 9.6 `GET /isolation/serializable`

```
t=0s   A (SERIALIZABLE): UPDATE 1000 → 500 (holds an exclusive row lock for 5 s)
t=1s   B (SERIALIZABLE): SELECT … ⏳ blocked — has to wait for A
t=5s   A: COMMIT → B's first read finally runs → 500
t=8s   B: second read → 500
```
Readers wait for writers: **perfect consistency, lowest concurrency.**

### Summary of what you should observe (MySQL/InnoDB)

| Endpoint | Level used by B | Anomaly shown? |
|---|---|---|
| `/readUnCommit` | READ_UNCOMMITTED | ✅ Dirty read |
| `/readCommit` | READ_COMMITTED | ✅ Non-repeatable read (no dirty read) |
| `/repeatableRead` | REPEATABLE_READ | ❌ none — value stays 1000 |
| `/phantomProblem` | READ_COMMITTED | ✅ Phantom read |
| `/phantomSolution` | SERIALIZABLE | ❌ none — writer is blocked |
| `/serializable` | SERIALIZABLE | ❌ none — reader is blocked |

> Timing is sleep-based, so a very slow first request (class loading, connection pool warm-up) can shift the interleaving.
> If you see an unexpected result, simply reset the data and run it again.

---

## 10. Reading the transaction logs

`TransactionManagerConfig` prints one line per **real** transaction event:

```
Transaction Logger: New Transaction Started (propagation: )0name: com.yasirkhan.transactional.services.WalletService.transfer
Transaction Logger: Transaction Rollbacker (name: com.yasirkhan.transactional.services.UserService.credit
```

Decode the numbers using Spring's `TransactionDefinition` constants:

| Value | Propagation | Value | Propagation |
|:---:|---|:---:|---|
| 0 | REQUIRED | 4 | NOT_SUPPORTED |
| 1 | SUPPORTS | 5 | NEVER |
| 2 | MANDATORY | 6 | NESTED |
| 3 | REQUIRES_NEW | | |

**How to read an experiment:** count the `New Transaction Started` lines for one request.
- Scenario A → one transaction (`transfer`).
- Scenario B → two (`transfer`, then `credit`), and `credit` rolls back first.
- Scenario C → you will also see tiny transactions named `SimpleJpaRepository.findById` / `SimpleJpaRepository.save` — Spring Data wraps its own repository methods in transactions, and with no outer tx each one commits immediately.

For even more detail add to `application.properties`:

```properties
logging.level.org.springframework.transaction.interceptor=TRACE
logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
```
This prints `Getting transaction for [...]`, `Participating in existing transaction`, `Suspending current transaction`, `Completing transaction for [...]` — the vocabulary of propagation.

`spring.jpa.show-sql=true` is already on, so you also see the SQL (`update users set ...`) in the same console.

---

## 11. Running the automated test

```bash
./mvnw test        # Windows: mvnw.cmd test
```

The only test is `TransactionalApplicationTests.contextLoads()` — a smoke test that the Spring context starts.
Because it uses the real `application.properties`, **MySQL must be running and the `transaction` database must exist**, otherwise it fails with a connection error.

The labs themselves are tested **manually** with the endpoints above, because they depend on real concurrency and timing against a real database.

---

## 12. Known issues & gotchas in this code

Useful to know while learning (none stop the project from working):

| # | Where | Observation |
|---|---|---|
| 1 | `WalletService` | Uses **`jakarta.transaction.Transactional`**, not Spring's `org.springframework.transaction.annotation.Transactional`. Spring honours both, but the Jakarta version has **no `isolation`, `readOnly`, or `NESTED`** and uses different attribute names. The imported-but-unused `Propagation` (Spring) suggests the intent to use Spring's — prefer it. |
| 2 | `UserController` | `UserRequest` has `@NotBlank` / `@NotEmpty`, but the controller parameter has **no `@Valid`**, so the constraints are never enforced. Sending no `contacts` causes a `NullPointerException` → HTTP 500. |
| 3 | `User.balance` | `double` for money causes rounding errors. Use `BigDecimal` in real code. |
| 4 | `TransactionManagerConfig` | Log text typos: `"(propagation: )"` misplaced parenthesis, `"Rollbacker"`, unclosed `(name: ...`. Cosmetic. |
| 5 | `RepeatableRead.java` | Stray `import static ...AbstractPersistable_.id;` (IDE auto-import accident). Several other unused imports exist (`UserService`, `Isolation`, `TransactionAspectSupport`, …). |
| 6 | `RepeatableRead.testPhantomReadProblem` | Thread B's catch block prints `"Error occurred in Thread A"` — copy-paste slip. |
| 7 | Demo output | `"Transaction A: Committed the update"` is printed **just before** the real commit (the commit happens after the method returns). Use the `Transaction Logger` lines for the true moment. |
| 8 | `UserRepository` | `findByIdWithPessimisticLock` (`PESSIMISTIC_WRITE`) is defined but **never used** yet — a ready-made starting point for a lost-update exercise. |
| 9 | `application.properties` | Plain-text DB credentials and `ddl-auto=update` are fine for a local lab only. H2 dependencies/console are present but unused. |
| 10 | `GET /users` | `contacts` is a lazy collection serialized after the service returns. This works because Spring Boot's *Open-EntityManager-In-View* is on by default. |
| 11 | `data/` folder | Contains leftover H2 database files from an earlier experiment. Do not commit them (`git rm -r --cached data` and add `data/` to `.gitignore`). |
| 12 | Wallet transfer to self | With `REQUIRES_NEW` on `credit`, `senderId == receiverId` makes the inner tx wait for a row lock held by the outer tx → waits until InnoDB's lock-wait timeout (default 50 s). A nice real-world deadlock-like example. |

---

## 13. Ideas to extend the lab

1. **Try the other propagations** on `credit()`: `MANDATORY`, `NEVER`, `SUPPORTS`, `NESTED` — predict the result *first*, then verify.
2. **Catch and continue:** wrap `userService.credit(...)` in `try/catch` inside `transfer()`. Does the outer transaction still commit? (Hint: depends on propagation — with plain `REQUIRED`, the shared tx is marked rollback-only and you get `UnexpectedRollbackException`.)
3. **Checked exception rollback:** throw a checked exception and observe that the tx commits; then add `rollbackFor = Exception.class`.
4. **Lost update:** run two concurrent debits on the same user, then fix it with `findByIdWithPessimisticLock` or an `@Version` optimistic lock.
5. **Self-invocation proof:** call a `@Transactional(REQUIRES_NEW)` method from another method of the *same* class and watch the annotation do nothing.
6. **Write real tests:** use `@SpringBootTest` + `CompletableFuture`/`CountDownLatch` instead of `Thread.sleep` to make the isolation scenarios deterministic.
7. **Switch databases:** run the same labs on PostgreSQL and compare (PostgreSQL's `READ_UNCOMMITTED` behaves like `READ_COMMITTED`; no dirty reads).

---

## 14. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Unknown database 'transaction'` | Create it first (section 6.1). |
| `Communications link failure` / connection refused | MySQL not running or wrong port. |
| `Access denied for user 'root'` | Password in `application.properties` doesn't match your MySQL. |
| `/isolation/*` returns **404** `No value present` | User id `2` doesn't exist — seed data (section 6.4). |
| Isolation results look "wrong" on the second run | Balance was changed by the previous run — use the Reset SQL. |
| `Lock wait timeout exceeded` | A transaction waited > 50 s for a lock (e.g. transfer-to-self with `REQUIRES_NEW`). Restart the app and retry. |
| Port 8080 already in use | `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` |
| Lombok errors in the IDE | Install the Lombok plugin and enable *annotation processing*. |

---

*Author: Yasir Khan — a learning project for Spring `@Transactional`, propagation, and isolation.*
