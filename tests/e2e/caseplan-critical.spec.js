const { test, expect } = require("@playwright/test");

function uniqueSuffix() {
  return `${Date.now()}${Math.floor(Math.random() * 1000)}`;
}

test("api contract: submit returns queued response", async ({ request }) => {
  const suffix = uniqueSuffix();
  const res = await request.post(
    "https://mc94chabh2.execute-api.us-east-2.amazonaws.com/orders",
    {
      data: {
        clientFirstName: `E2E${suffix}`,
        clientLastName: "Flow",
        attorneyName: "E2E Attorney",
        barNumber: `BAR-E2E-${suffix}`,
        primaryCauseOfAction: "Contract",
        remedySought: "Damages"
      }
    }
  );
  expect(res.ok()).toBeTruthy();
  const body = await res.json();
  expect(body.message).toBe("queued");
  expect(body.status).toBe("pending");
  expect(Number.isInteger(body.id)).toBeTruthy();
});

test("ui criticals: bar template, view content, download doc", async ({
  page
}) => {
  await page.goto("/", { waitUntil: "domcontentloaded" });

  const barInput = page.getByLabel("Bar Number * (e.g. BAR-12345678-1234)");
  await expect(barInput).toHaveAttribute("placeholder", "");

  const firstCompletedRow = page.locator("tbody tr", {
    has: page.locator(".status.completed")
  });
  await expect(firstCompletedRow.first()).toBeVisible();

  await firstCompletedRow
    .first()
    .getByRole("button", { name: "View" })
    .click();
  await expect(page.getByText(/Case Plan Detail #/)).toBeVisible();
  await expect(page.locator(".modal-body pre")).not.toHaveText(
    "No content available."
  );

  const downloadPromise = page.waitForEvent("download");
  await page.locator(".modal").getByRole("button", { name: "Download .doc" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/\.doc$/);
});
