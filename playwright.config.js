// @ts-check
const { defineConfig } = require("@playwright/test");

const frontendURL = process.env.FRONTEND_URL;
const baseURL = frontendURL || "http://localhost:3000";

module.exports = defineConfig({
  testDir: "./tests/e2e",
  timeout: 120000,
  expect: {
    timeout: 30000
  },
  use: {
    baseURL,
    headless: true,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    acceptDownloads: true
  },
  reporter: [["list"], ["html", { open: "never" }]]
  ,
  webServer: frontendURL
    ? undefined
    : {
        command: "python3 -m http.server 3000 -d src/main/resources/static",
        url: "http://localhost:3000",
        reuseExistingServer: true,
        timeout: 120000
      }
});
