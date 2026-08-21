const { test, expect } = require("@playwright/test");
const { execSync } = require("child_process");

// Real end-to-end: browser -> local Spring Boot app -> real Lambda handler code -> real Postgres/Redis.
// No API mocks. The backend must be running with the background consumer disabled
// (caseplan.consumer.enabled=false) so newly created plans stay in "pending".
const BACKEND = process.env.FRONTEND_URL || "http://localhost:3000";

// Unique per run so the backend's same-day duplicate detection never trips
// on data left over from previous runs.
function uniqueName(prefix) {
  const suffix = Math.random().toString(36).replace(/[^a-z]/gi, "").slice(0, 6);
  return `${prefix}${suffix}`;
}

function uniqueBarNumber() {
  const digits = () =>
    String(Math.floor(Math.random() * 100000000)).padStart(8, "0");
  const tail = () =>
    String(Math.floor(Math.random() * 10000)).padStart(4, "0");
  return `BAR-${digits()}-${tail()}`;
}

let seededPlanId;
let seededClientName;
let seededBarNumber;

function runPsql(sql) {
  // Locally: the dev Postgres runs as the docker-compose "db" container.
  // In CI: connect to the GitHub service container (E2E_PSQL carries the prefix).
  const psql = process.env.E2E_PSQL
    ? `${process.env.E2E_PSQL} -c "${sql}"`
    : `docker exec caseplan-db-1 psql -U dev_xh -d dev_db -c "${sql}"`;
  execSync(psql, { stdio: "ignore" });
}

function seedCompletedPlan() {
  // Create a plan through the real API (lands in "pending"), then mark it
  // completed with generated content directly in the dev database.
  seededClientName = uniqueName("Mia");
  seededBarNumber = uniqueBarNumber();
  const res = execSync(
    `curl -s -X POST ${BACKEND}/orders -H 'Content-Type: application/json' -d '{"clientFirstName":"${seededClientName}","clientLastName":"Johnson","attorneyName":"Ethan Cole","barNumber":"${seededBarNumber}","primaryCauseOfAction":"Contract","remedySought":"Damages","confirm":true}'`
  );
  const created = JSON.parse(res.toString());
  if (!created.id) {
    throw new Error(`seed POST failed: ${res.toString()}`);
  }
  runPsql(
    `update dev_caseplans set status='completed', generated_plan='# Legal Service Plan' where id=${created.id}`
  );
  return created.id;
}

test.beforeAll(() => {
  seededPlanId = seedCompletedPlan();
});

test("ui submit flow: real create + queued ack + list refresh", async ({ page }) => {
  const clientFirst = uniqueName("Olivia");
  const barNumber = uniqueBarNumber();

  await page.goto("/", { waitUntil: "domcontentloaded" });

  await page.getByLabel("Client First Name *").fill(clientFirst);
  await page.getByLabel("Client Last Name *").fill("Martin");
  await page.getByLabel("Attorney Name *").fill("Mason Reed");
  await page
    .getByLabel("Bar Number * (e.g. BAR-12345678-1234)")
    .fill(barNumber);
  await page
    .getByLabel("Docket Number (Optional, e.g. 2026-CV-123456)")
    .fill("2026-CV-654321");
  await page.getByLabel("Primary Cause of Action *").fill("Personal Injury");
  await page.getByLabel("Remedy Sought *").fill("Compensation");

  await page.getByRole("button", { name: "Submit Case" }).click();

  // Real POST /orders -> queued ack with the new order id
  await expect(
    page.getByText(/Submitted successfully\. Order #\d+ queued\./)
  ).toBeVisible();
  await expect(page.getByLabel("Client First Name *")).toHaveValue("");

  // Newest record shows up first with the generated service number and pending badge
  const firstRow = page.locator("tbody tr").first();
  await expect(firstRow).toContainText(`${clientFirst} Martin`);
  await expect(firstRow).toContainText(/#SRV-\d{8}-\d{4}/);
  await expect(firstRow.locator(".status.pending")).toBeVisible();

  await expect(page.getByRole("button", { name: "Submit Case" })).toBeEnabled();
});

test("ui criticals: service number visible, view rendered, download doc", async ({
  page
}) => {
  await page.goto("/", { waitUntil: "domcontentloaded" });

  const completedRow = page.locator("tbody tr", {
    has: page.locator(".status.completed")
  });
  await expect(completedRow.first()).toContainText(`${seededClientName} Johnson`);
  await expect(completedRow.first()).toContainText(/#SRV-\d{8}-\d{4}/);
  await expect(
    completedRow.first().getByRole("button", { name: "View" })
  ).toBeEnabled();

  await completedRow.first().getByRole("button", { name: "View" }).click();

  // Real GET /orders/{id} -> generated content rendered in the modal
  await expect(
    page.getByText(`Case Plan Detail #${seededPlanId}`)
  ).toBeVisible();
  await expect(page.locator(".modal-body h1")).toHaveText("Legal Service Plan");

  const downloadPromise = page.waitForEvent("download");
  await page
    .locator(".modal")
    .getByRole("button", { name: "Download .doc" })
    .click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/\.doc$/);
});

test("ui validation: invalid bar number is blocked before submit", async ({
  page
}) => {
  let postCount = 0;
  page.on("request", (request) => {
    if (request.method() === "POST" && request.url().includes("/orders")) {
      postCount += 1;
    }
  });

  await page.goto("/", { waitUntil: "domcontentloaded" });

  await page.getByLabel("Client First Name *").fill("Olivia");
  await page.getByLabel("Client Last Name *").fill("Martin");
  await page.getByLabel("Attorney Name *").fill("Mason Reed");
  await page
    .getByLabel("Bar Number * (e.g. BAR-12345678-1234)")
    .fill("BAD-BAR");
  await page.getByLabel("Primary Cause of Action *").fill("Personal Injury");
  await page.getByLabel("Remedy Sought *").fill("Compensation");

  await page.getByRole("button", { name: "Submit Case" }).click();
  await expect(
    page.getByText("Bar number must match BAR-12345678-1234.")
  ).toBeVisible();
  expect(postCount).toBe(0);
});
