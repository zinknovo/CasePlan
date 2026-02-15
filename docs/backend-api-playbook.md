<p align="right">
  <a href="./backend-api-playbook.zh-CN.md"> <img alt="中文" src="https://img.shields.io/badge/中文-2da44e?style=for-the-badge"> </a>
</p>

# Backend API Playbook (Entry-Level Friendly)

This is the concrete workflow for building backend APIs in this project.

## 1) What This Process Is Called

- Broad name: `SDLC` (Software Development Lifecycle)
- Team execution name: `Change Delivery Lifecycle`
- Pipeline name: `CI/CD` (build, test, verify, deploy)

For our day-to-day work, you can treat it as:
`Design -> Implement -> Validate -> Ship -> Observe`

## 2) Concrete Coding Workflow (Micro View)

Use this sequence when adding an endpoint.

1. Write API contract first
- Define path, method, request body, response body, status codes.
- Example status policy:
  - `201`: created new record
  - `200`: idempotent duplicate match
  - `400`: invalid input
  - `409`: business conflict
  - `404`: resource not found
  - `202`: async accepted, not completed

2. Keep controller thin
- Controller should do:
  - parse/validate input
  - call service
  - map result to HTTP status
- Do not put complex business decisions in controller.

3. Put business rules in service
- Service handles:
  - duplicate detection
  - status transitions
  - conflict checks
  - idempotency behavior

4. Keep repository focused on queries
- Repository should only provide query methods.
- No business branching logic in repository layer.

5. Standardize errors
- Use common exception types:
  - `ValidationException` -> `400`
  - `BlockException` -> `409`
- Keep error response shape consistent.

6. Add branch-oriented tests
- For each important branch, add at least one test:
  - happy path
  - duplicate path
  - conflict path
  - validation path
  - not-found path
  - async acceptance path (if any)

7. Refactor duplication immediately
- If two controllers/services build the same response or use same logic, extract utility/helper early.

## 3) Whole-Picture Workflow (Macro View)

1. Requirements and domain
- Clarify resource, ownership, status machine, and constraints.

2. API contract
- Create/update OpenAPI first: `docs/api/openapi.yaml`.

3. Implementation
- DTO -> Controller -> Service -> Repository.

4. Verification gate
- Run:
  - `mvn test`
  - `mvn -DskipITs verify`
- Coverage gate must pass (`line >= 90%`, `branch >= 90%`).

5. Runtime sanity
- Boot app and check startup logs:
  - `mvn -DskipTests spring-boot:run`
- Confirm no startup failure.

6. Deployment/infra check
- Confirm route exposure model is still correct (Web vs Lambda).
- Confirm CORS methods match API methods.
- Confirm environment vars and IAM permissions are still enough.

7. PR quality check
- Use `.github/pull_request_template.md`.
- Include API examples and testing evidence.

## 4) Quick Checklist Before Merge

- [ ] OpenAPI updated (`docs/api/openapi.yaml`)
- [ ] Controller remains thin
- [ ] Service owns business rules
- [ ] All status codes are intentional and tested
- [ ] `mvn test` passes
- [ ] `mvn -DskipITs verify` passes
- [ ] Coverage gate passes
- [ ] App startup sanity check done
- [ ] Infra/CORS/routing impact reviewed

## 5) Why `202` for Retry Endpoints

Use `202 Accepted` when request triggers async processing.

- `200` implies request completed now.
- `201` implies a new resource was created now.
- `202` means request accepted and work will continue in background.
