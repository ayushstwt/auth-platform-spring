# Known Issues & Backlog for v1.1.0

The following items were identified during the 1.0.0 independent release review as Medium and Low severity enhancements. None of these block production readiness, and they are formatted for direct tracking as GitHub issues.

---

### Issue 1: [Medium] Chunked Batching for Retention Hard-Purge Job
- **Severity**: Medium
- **Labels**: `performance`, `database`, `enhancement`
- **Description**:
  The scheduled account hard-purge job (`AccountPurgeScheduledService` calling `hardPurgeUsersBefore`) executes a single delete statement against soft-deleted users past retention. In databases with large volumes of deleted accounts, this could lead to prolonged table write locks and replication lag.
- **Proposed Solution**:
  Implement chunked batch deletion (e.g. `DELETE ... WHERE id IN (SELECT id FROM users ... LIMIT 5000)`) in a loop until zero rows are affected.

---

### Issue 2: [Medium] User-Agent Parsing Library for Enhanced Device Intelligence
- **Severity**: Medium
- **Labels**: `security`, `session-management`
- **Description**:
  `SessionManagementService.parseSimpleDevice()` currently employs string substring heuristics (`Mobile`, `Macintosh`, `Windows`, `Linux`) to present the device name in session listings.
- **Proposed Solution**:
  Incorporate a lightweight user-agent parser (such as Yauaa or user-agent-utils) to detect exact browser and client device hardware for security login notifications.

---

### Issue 3: [Medium] Redis Cluster and Sentinel Support for Distributed Rate Limiter
- **Severity**: Medium
- **Labels**: `infrastructure`, `resilience`
- **Description**:
  Lettuce connections in `RedisBucket4jRateLimiter` support standard standalone Redis hosts.
- **Proposed Solution**:
  Expose Redis cluster and Sentinel topology configuration properties (`spring.data.redis.cluster.nodes`, `spring.data.redis.sentinel.master`) to support AWS ElastiCache / Redis Enterprise multi-node configurations seamlessly.

---

### Issue 4: [Low] Automated Static OpenAPI Export Maven Goal
- **Severity**: Low
- **Labels**: `tooling`, `developer-experience`
- **Description**:
  The OpenAPI contract is committed in `docs/openapi.json`.
- **Proposed Solution**:
  Integrate `springdoc-openapi-maven-plugin` execution into the Maven build lifecycle so `openapi.json` regenerates automatically on build.
