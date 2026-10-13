// Accessibility and SEO check for the running site: axe-core's WCAG 2.1 A rules plus AA colour contrast, then
// the page's title, description, headings and image alt text, and whether it still fits at 200% zoom.
//
//   bun run a11y-check / /catalog /bag                          against https://localhost:5443
//   bun run a11y-check --base=https://localhost:15443 /about     another copy of the stack
//   bun run a11y-check --login=email:password /account /account/orders   signed in first
//
// 200% zoom is checked as a 640 px wide window: zooming a 1280 px window to 200% lays the page out at 640
// CSS pixels. Needs Chromium's headless shell, like layout-check.mjs.
import fs from 'node:fs';
import { createRequire } from 'node:module';
import { chromium } from 'playwright-core';

const args = process.argv.slice(2);
const opt = (k, d) => args.find((x) => x.startsWith(`--${k}=`))?.split('=').slice(1).join('=') ?? d;
const base = opt('base', 'https://localhost:5443');
const paths = args.filter((a) => !a.startsWith('--'));
if (!paths.length) {
  console.log('Usage: bun run a11y-check [--base=https://localhost:5443] /path ...');
  process.exit(1);
}
const axeSource = fs.readFileSync(createRequire(import.meta.url).resolve('axe-core/axe.min.js'), 'utf8');

const browser = await chromium.launch(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {});
const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1280, height: 900 } });
const login = opt('login', '');
if (login) {
  // the refresh cookie this sets is what the app restores the session from on each page load
  const [email, ...rest] = login.split(':');
  const res = await context.request.post(`${base}/auth/login`, { data: { email, password: rest.join(':') } });
  if (!res.ok()) throw new Error(`sign-in failed: ${res.status()}`);
}
let problems = 0;
for (const path of paths) {
  const page = await context.newPage();
  // 'load', then a bounded wait for quiet: Stripe's telemetry can keep a later page from ever going idle
  await page.goto(base + path, { waitUntil: 'load' });
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(500);
  await page.addScriptTag({ content: axeSource });
  const axe = await page.evaluate(async () => {
    // WCAG 2.1 A, plus AA's 4.5:1 contrast rule the brief names (not AAA's 7:1, color-contrast-enhanced)
    // eslint-disable-next-line no-undef
    const r = await axe.run(document, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag21a'] },
      rules: { 'color-contrast': { enabled: true } },
    });
    return r.violations.map((v) => ({ id: v.id, impact: v.impact, count: v.nodes.length, where: v.nodes[0]?.target.join(' ') }));
  });
  const seo = await page.evaluate(() => {
    const levels = [...document.querySelectorAll('h1,h2,h3,h4,h5,h6')]
      .filter((h) => h.getClientRects().length).map((h) => Number(h.tagName[1]));
    const skips = levels.filter((l, i) => i > 0 && l > levels[i - 1] + 1).length;
    return {
      title: document.title,
      description: document.querySelector('meta[name="description"]')?.content ?? '',
      h1: levels.filter((l) => l === 1).length,
      skips,
      noAlt: [...document.querySelectorAll('img:not([alt])')].length,
    };
  });
  await page.setViewportSize({ width: 640, height: 900 });
  await page.waitForTimeout(300);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  await page.close();

  const notes = [];
  if (seo.title.length >= 60) notes.push(`title is ${seo.title.length} characters`);
  if (seo.description.length < 155 || seo.description.length > 160) notes.push(`description is ${seo.description.length} characters`);
  if (seo.h1 !== 1) notes.push(`${seo.h1} h1 headings`);
  if (seo.skips) notes.push(`${seo.skips} heading level skips`);
  if (seo.noAlt) notes.push(`${seo.noAlt} images without alt`);
  if (overflow > 1) notes.push(`${overflow}px wider than the window at 200% zoom`);
  for (const v of axe) notes.push(`axe ${v.id} (${v.impact}) x${v.count}: ${v.where}`);
  problems += notes.length;
  console.log(`${notes.length ? 'x' : 'ok'} ${path}  "${seo.title}"`);
  for (const n of notes) console.log(`    ${n}`);
}
await browser.close();
console.log(problems ? `\n${problems} findings` : '\nNo findings');
process.exit(problems ? 1 : 0);
