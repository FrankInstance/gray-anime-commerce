import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 30_000,
  expect: {
    timeout: 7_000
  },
  reporter: 'list',
  outputDir: 'output/playwright',
  use: {
    baseURL: process.env.CORE_WEB_BASE_URL ?? 'http://127.0.0.1',
    channel: 'chrome',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  }
});
