const { test, expect } = require("@playwright/test");

function completedPlan() {
  return {
    id: 101,
    status: "completed",
    createdAt: "2026-02-16T03:20:00Z",
    caseInfo: {
      serviceNumber: "SRV-20260216-0001",
      docketNumber: "2026-CV-123456",
      primaryCauseOfAction: "Contract",
      remedySought: "Damages",
      client: {
        firstName: "Mia",
        lastName: "Johnson"
      },
      attorney: {
        name: "Ethan Cole",
        barNumber: "BAR-12345678-1234"
      }
    }
  };
}

test("ui submit flow: queued ack + keep intake available", async ({ page }) => {
  let queued = false;

  await page.route("**/orders", async (route) => {
    const method = route.request().method();
    if (method === "GET") {
      const items = queued
        ? [
            {
              id: 202,
              status: "pending",
              createdAt: "2026-02-16T03:25:00Z",
              caseInfo: {
                serviceNumber: "SRV-20260216-0002",
                docketNumber: "2026-CV-654321",
                primaryCauseOfAction: "Personal Injury",
                remedySought: "Compensation",
                client: {
                  firstName: "Olivia",
                  lastName: "Martin"
                },
                attorney: {
                  name: "Mason Reed",
                  barNumber: "BAR-87654321-4321"
                }
              }
            },
            completedPlan()
          ]
        : [completedPlan()];

      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ count: items.length, items })
      });
      return;
    }

    if (method === "POST") {
      queued = true;
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({ id: 202, status: "pending", message: "queued" })
      });
      return;
    }

    await route.fallback();
  });

  await page.route("**/orders/101", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: 101,
        status: "completed",
        generatedPlan: "# Warmed preview"
      })
    });
  });

  await page.goto("/", { waitUntil: "domcontentloaded" });

  const barInput = page.getByLabel("Bar Number * (e.g. BAR-12345678-1234)");
  await expect(barInput).toHaveAttribute("placeholder", "");

  const docketInput = page.getByLabel(
    "Docket Number (Optional, e.g. 2026-CV-123456)"
  );
  await expect(docketInput).toHaveAttribute("placeholder", "");

  await page.getByLabel("Client First Name *").fill("Olivia");
  await page.getByLabel("Client Last Name *").fill("Martin");
  await page.getByLabel("Attorney Name *").fill("Mason Reed");
  await page
    .getByLabel("Bar Number * (e.g. BAR-12345678-1234)")
    .fill("BAR-87654321-4321");
  await docketInput.fill("2026-CV-654321");
  await page.getByLabel("Primary Cause of Action *").fill("Personal Injury");
  await page.getByLabel("Remedy Sought *").fill("Compensation");

  await page.getByRole("button", { name: "Submit Case" }).click();

  await expect(page.getByText("Submitted successfully. Order #202 queued.")).toBeVisible();
  await expect(page.getByLabel("Client First Name *")).toHaveValue("");

  const firstRow = page.locator("tbody tr").first();
  await expect(firstRow).toContainText("Olivia Martin");
  await expect(firstRow).toContainText("#SRV-20260216-0002");
  await expect(firstRow.locator(".status.pending")).toBeVisible();

  await expect(page.getByRole("button", { name: "Submit Case" })).toBeEnabled();
});

test("ui criticals: service number visible, view rendered, download doc", async ({
  page
}) => {
  await page.route("**/orders", async (route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ count: 1, items: [completedPlan()] })
      });
      return;
    }
    await route.fallback();
  });

  await page.route("**/orders/101", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: 101,
        status: "completed",
        generatedPlan:
          "# Legal Service Plan\n\n## Problem List\n- Contract dispute\n\n## Goals\n- Reach settlement"
      })
    });
  });

  await page.goto("/", { waitUntil: "domcontentloaded" });

  const firstCompletedRow = page.locator("tbody tr", {
    has: page.locator(".status.completed")
  });
  await expect(firstCompletedRow.first()).toContainText("#SRV-20260216-0001");
  await expect(
    firstCompletedRow.first().getByRole("button", { name: "View" })
  ).toBeEnabled();

  await firstCompletedRow
    .first()
    .getByRole("button", { name: "View" })
    .click();

  await expect(page.getByText(/Case Plan Detail #101/)).toBeVisible();
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
  let postCalled = false;

  await page.route("**/orders", async (route) => {
    const method = route.request().method();
    if (method === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ count: 0, items: [] })
      });
      return;
    }
    if (method === "POST") {
      postCalled = true;
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({ id: 999, status: "pending", message: "queued" })
      });
      return;
    }
    await route.fallback();
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
  expect(postCalled).toBeFalsy();
});
