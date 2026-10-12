// Layout check for the running site: opens each page at each screen width and lists what looks misaligned.
//
//   bun run layout-check /catalog /bag                          against `bun dev` on localhost:5173
//   bun run layout-check --widths=390,1440 --login=email:pw /account
//   bun run layout-check --mock=mocks.mjs /admin/orders          answers some API calls from a file
//   bun run layout-check --shots /                               also saves full-page screenshots
//
// --mock names a module whose default export is [RegExp, body, status?] triples, matched against the
// request's path and query: how to see pages whose endpoints the backend doesn't have yet.
// Findings marked (info) are worth a look, not errors. Known false alarms: a photo cropped by its tile
// on purpose (clip), text hidden by a line clamp (overlap), a first line that sits inside <b> (control).
// Needs Chromium's headless shell: `bunx playwright-core install chromium-headless-shell`, or CHROME_PATH.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { chromium } from 'playwright-core';
import { measure } from './layout-measure.mjs';

const args = process.argv.slice(2);
const opt = (k, d) => args.find((x) => x.startsWith(`--${k}=`))?.split('=').slice(1).join('=') ?? d;
const widths = opt('widths', '390,768,1024,1440').split(',').map(Number);
const base = opt('base', 'http://localhost:5173');
const shots = args.includes('--shots');
const MAX = Number(opt('max', '8'));
const paths = args.filter((a) => !a.startsWith('--'));
if (!paths.length) {
  console.log('Usage: bun run layout-check [--widths=390,1440] [--login=email:password] [--mock=file.mjs] [--shots] /path ...');
  process.exit(1);
}

/** CHROME_PATH, or the headless shell Playwright installs under ~/.cache/ms-playwright. */
function browserPath() {
  if (process.env.CHROME_PATH) return process.env.CHROME_PATH;
  const root = path.join(os.homedir(), '.cache', 'ms-playwright');
  const dir = fs.existsSync(root) && fs.readdirSync(root).filter((d) => d.startsWith('chromium_headless_shell-')).sort().pop();
  const exe = dir && path.join(root, dir, 'chrome-headless-shell-linux64', 'chrome-headless-shell');
  if (exe && fs.existsSync(exe)) return exe;
  console.error('No headless Chromium found. Run `bunx playwright-core install chromium-headless-shell`, or set CHROME_PATH.');
  process.exit(1);
}

const mocks = opt('mock') ? (await import(path.resolve(opt('mock')))).default : [];
const browser = await chromium.launch({ executablePath: browserPath() });
const context = await browser.newContext({ deviceScaleFactor: 1 });
for (const [pattern, body, status = 200] of mocks) {
  await context.route((u) => pattern.test(u.pathname + u.search), (r) =>
    r.fulfill({ status, contentType: 'application/json', body: JSON.stringify(typeof body === 'function' ? body() : body) }));
}
// one sign-in for the whole run: the login endpoint is rate limited, and the cookie is shared by every page
if (opt('login')) {
  const [email, password] = opt('login').split(':');
  const page = await context.newPage();
  await page.goto(base, { waitUntil: 'networkidle' }).catch(() => {});
  const status = await page.evaluate(async ([email, password]) =>
    (await fetch('/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) })).status,
  [email, password]);
  if (status !== 200) { console.error(`Sign-in failed (${status}).`); process.exit(1); }
  await page.close();
}
if (shots) fs.mkdirSync('layout-shots', { recursive: true });

let total = 0;
for (const p of paths) for (const w of widths) {
  const page = await context.newPage();
  await page.setViewportSize({ width: w, height: 900 });
  await page.goto(base + p, { waitUntil: 'networkidle' }).catch(() => {});
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
  const checks = await page.evaluate(measure, MAX);
  const overx = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (overx > 0) (checks.overx ||= []).push(`page scrolls sideways by ${overx}px`);
  const n = Object.entries(checks).filter(([k]) => !k.includes('info')).reduce((s, [, v]) => s + v.length, 0);
  total += n;
  console.log(`\n${p} @${w}${n ? `  [${n}]` : '  clean'}`);
  for (const [k, v] of Object.entries(checks)) for (const m of v) console.log(`  ${k.padEnd(12)} ${m}`);
  if (shots) await page.screenshot({ path: `layout-shots/${p.replace(/[^a-z0-9]+/gi, '_')}-${w}.png`, fullPage: true });
  await page.close();
}
console.log(`\n${total} findings`);
await browser.close();
process.exit(total ? 2 : 0);
