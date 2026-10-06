#!/usr/bin/env node
/**
 * Fails (exit 1) if the production bundle leaks a secret.
 *
 * Checks every file under dist/trade-ui for:
 *   1. source maps (a .map file is the source code back again)
 *   2. the market-data host and key/secret patterns
 *   3. the literal value of any variable in a real .env file, under whatever name it was copied to
 *   4. (criteria 2) the UI source never talks to Fauxnance: no market-data host or key, and no absolute URL
 *      other than our own trade-api / auth services
 *   5. (task 3) on a leak, prints which secrets to revoke and how; fails if there are no .env values to compare
 *
 * .env.example files are ignored: they hold dummy values. Secret values are never printed.
 *
 * Usage: npm run check:bundle            (builds first)
 *        SKIP_BUILD=1 npm run check:bundle   (scan the existing dist)
 */
const fs = require('node:fs');
const path = require('node:path');
const { execSync } = require('node:child_process');

const UI_ROOT = path.resolve(__dirname, '..');
const REPO_ROOT = path.resolve(UI_ROOT, '..', '..');
const DIST = path.join(UI_ROOT, 'dist', 'trade-ui');

const PATTERNS = [
  /execute-api/i,
  /amazonaws/i,
  /fauxnance/i,
  /x-api-key/i,
  /api[_-]?key/i,
  /jwt_secret/i,
  /shared-secret/i,
  /client_secret/i,
  /private[_-]?key/i,
  /sourceMappingURL/i
];
// Absolute URLs the UI source may contain: our own services on localhost (see src/app/api/provide-api.ts),
// Google Fonts, and github.com links in generated-code comments. Any other host fails the check.
const ALLOWED_URL = /^https?:\/\/(localhost(:\d+)?|fonts\.googleapis\.com|fonts\.gstatic\.com|github\.com)(?![\w.-])/;
const URL_PATTERN = /https?:\/\/[^\s'"`)>]+/g;
const MIN_VALUE_LENGTH = 8;
const GENERIC_VALUES = new Set(['localhost', '127.0.0.1', 'true', 'false']);
const SKIP_DIRS = new Set(['node_modules', '.git', 'target', 'dist', '.angular']);

function walk(dir, visit) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (!SKIP_DIRS.has(entry.name)) walk(full, visit);
    } else {
      visit(full);
    }
  }
}

/** Values of every variable in every real `.env` file in the repo (exact name `.env` only). */
function loadSecretValues() {
  const found = [];
  walk(REPO_ROOT, (file) => {
    if (path.basename(file) !== '.env') return;
    for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
      const m = line.match(/^\s*(?:export\s+)?([A-Za-z0-9_]+)\s*=\s*(.*)$/);
      if (!m) continue;
      const value = m[2].trim().replace(/^(['"])(.*)\1$/, '$2');
      if (value.length >= MIN_VALUE_LENGTH && !GENERIC_VALUES.has(value)) {
        found.push({ name: m[1], value, file: path.relative(REPO_ROOT, file) });
      }
    }
  });
  return found;
}

/** Criteria 2: the UI source never calls Fauxnance; only trade-api and auth URLs are allowed. */
function checkSource(problems) {
  const srcDir = path.join(UI_ROOT, 'src');
  let count = 0;
  walk(srcDir, (file) => {
    if (!/\.(ts|html|css|json)$/.test(file) || /\.spec\.ts$/.test(file)) return;
    count++;
    const rel = path.relative(UI_ROOT, file);
    const text = fs.readFileSync(file, 'utf8');
    for (const pattern of [/execute-api/i, /amazonaws/i, /x-api-key/i, /api[_-]?key/i]) {
      if (pattern.test(text)) problems.push(`source ${rel} matches ${pattern} (UI must not know the market-data service)`);
    }
    for (const url of text.match(URL_PATTERN) ?? []) {
      if (!ALLOWED_URL.test(url)) problems.push(`source ${rel} calls/mentions ${url}; only our own services are allowed`);
    }
  });
  return count;
}

/** Task 3: tell the developer exactly what to revoke for each leaked secret. */
function printRevocation(leakedNames) {
  console.error('\nTASK 3 - treat every leaked value as PUBLISHED. Revoke it; deleting the line is not enough:');
  for (const name of new Set(leakedNames)) {
    if (/FAUXNANCE.*KEY/i.test(name)) {
      console.error(`  - ${name}: request a new Fauxnance key, have the old one revoked, update the server-side .env (executor).`);
    } else if (/JWT.*SECRET/i.test(name)) {
      console.error(`  - ${name}: generate a new one (openssl rand -base64 32), redeploy nest-auth, trade-api and executor; old tokens stop working.`);
    } else {
      console.error(`  - ${name}: rotate this credential at its provider and update every service that uses it.`);
    }
  }
  console.error('Then remove it from the front end, rebuild, and re-run this check.');
}

function main() {
  if (process.env.SKIP_BUILD !== '1') {
    fs.rmSync(path.join(UI_ROOT, 'dist'), { recursive: true, force: true });
    try {
      execSync('npx ng build', { cwd: UI_ROOT, stdio: 'ignore' });
    } catch {
      console.error('Build failed.');
      process.exit(2);
    }
  }
  if (!fs.existsSync(DIST)) {
    console.error(`No build output at ${DIST}`);
    process.exit(2);
  }

  const files = [];
  walk(DIST, (f) => files.push(f));
  const secrets = loadSecretValues();
  const problems = [];
  const leakedNames = [];

  for (const file of files) {
    const rel = path.relative(UI_ROOT, file);
    if (file.endsWith('.map')) problems.push(`source map present: ${rel}`);

    const text = fs.readFileSync(file, 'utf8');
    for (const pattern of PATTERNS) {
      if (pattern.test(text)) problems.push(`pattern ${pattern} found in ${rel}`);
    }
    for (const s of secrets) {
      if (text.includes(s.value)) {
        problems.push(`value of ${s.name} (from ${s.file}) found in ${rel}`);
        leakedNames.push(s.name);
      }
    }
  }

  const sourceFiles = checkSource(problems);
  if (secrets.length === 0 && process.env.ALLOW_NO_ENV !== '1') {
    problems.push('no values found in any real .env file, so the literal-value check could not run (set ALLOW_NO_ENV=1 to skip)');
  }

  console.log(`Scanned ${files.length} files in ${path.relative(UI_ROOT, DIST)} and ${sourceFiles} UI source files`);
  console.log(`Checked ${secrets.length} secret values from real .env files (.env.example ignored)`);
  if (problems.length > 0) {
    problems.forEach((p) => console.error(`FAIL: ${p}`));
    if (leakedNames.length > 0 || problems.some((p) => /^pattern/.test(p))) printRevocation(leakedNames);
    process.exit(1);
  }
  console.log('OK: bundle is secret-free');
}

main();
