# CasePlan Monitoring Plan

Architecture: Spring Boot 2.7 + PostgreSQL + Redis Queue + LLM API (DeepSeek/OpenAI/Claude)

## Business Metrics

| Metric | Problem Detected | Alert Threshold |
|--------|-----------------|-----------------|
| CasePlan creations / min | Abnormal traffic (spike = abuse, drop = entry point down) | 50% drop or 5x spike vs baseline |
| CasePlan completion rate (completed / total) | LLM generation failures | < 80% for 10 min |
| CasePlan failure rate (failed / total) | Systemic LLM or consumer failure | > 10% for 5 min |
| Pending backlog count | Consumer can't keep up with producer | > 50 for 5 min |
| Stalled processing count | Worker stuck or crashed | > 0 for 10 min |
| Duplicate Block triggers / hour | Upstream sending duplicate submissions | 10x spike |
| Warning confirm ratio | Users frequently bypassing warnings (data quality issue) | Observe only |

## Performance Metrics

| Metric | Problem Detected | Alert Threshold |
|--------|-----------------|-----------------|
| POST /api/caseplans P95 latency | Create endpoint slow (DB queries, dedup logic) | > 500ms |
| GET /api/caseplans/{id}/status P95 latency | Status polling slow | > 200ms |
| LLM API call P95 latency | LLM provider responding slowly | > 30s |
| LLM average retry count per request | LLM instability triggering retries | > 1.5 |
| End-to-end CasePlan latency (created -> completed) | Overall processing pipeline slow | P95 > 2 min |
| Redis BLPOP to processing start delay | Queue consumption lag | > 5s |
| POST /api/intake P95 latency | Adapter parsing slow (large XML docs) | > 1s |

## Error Metrics

| Metric | Problem Detected | Alert Threshold |
|--------|-----------------|-----------------|
| HTTP 5xx count / min | Internal server errors | > 0 for 1 min |
| HTTP 400 count / min | Client submission format issues | 5x spike |
| LLM API call failure rate | LLM provider down or API key expired | > 20% for 3 min |
| LLM 3-retry exhaustion count / hour | Complete inability to generate plans | > 5 / hour |
| Stale recovery triggers | Worker crash recovery (indicates previous abnormal exit) | > 0 per startup |
| Intake adapter parse failure rate | Upstream data format changed | > 5% for 5 min |
| Uncaught exception count / min | Unhandled exception paths in code | > 0 |
| Redis connection failure count | Redis down or network issue | > 0 for 1 min |
| DB connection pool exhaustion count | Not enough database connections | > 0 |

## Resource Metrics

| Metric | Problem Detected | Alert Threshold |
|--------|-----------------|-----------------|
| JVM heap memory usage | Memory leak or insufficient config | > 85% for 5 min |
| JVM GC pause time / frequency | GC-induced latency | Single > 500ms or > 10/min |
| CPU usage | Compute bottleneck | > 80% for 5 min |
| PostgreSQL active connections / max | Connection pool near exhaustion | > 80% |
| PostgreSQL slow queries / min | Missing index or query degradation | > 5/min (queries > 1s) |
| Redis memory usage | Queue backlog consuming memory | > 80% maxmemory |
| Redis queue length (caseplan:pending) | Queue buildup from resource perspective | > 100 |
| Disk usage (DB data directory) | Data growth filling disk | > 85% |
| caseplan-consumer thread alive status | Worker thread unexpectedly exited | Thread absent = immediate alert |

## Priority Alerts (Top 5)

1. **HTTP 5xx > 0** - System error
2. **Pending backlog > 50** - Consumer stalled
3. **LLM failure rate > 20%** - LLM provider down
4. **Processing stalled > 10 min** - Worker dead
5. **Completion rate < 80%** - Business abnormality

## Recommended Stack

Spring Boot Actuator + Micrometer + Prometheus + Grafana
