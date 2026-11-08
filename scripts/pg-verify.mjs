// Runtime de verificação com PostgreSQL embutido (plano C do AGENTS.md).
// Sobe um cluster limpo em pasta temporária, aplica migrations em ordem,
// carrega o seed (se existir) e roda todos os db/tests/*.sql.
// É usado por scripts/verificar-db.sh quando docker e psql não estão disponíveis.

import { readFileSync, readdirSync, existsSync, mkdtempSync, rmSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import net from 'node:net';
import os from 'node:os';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const runtimeDir = process.env.MANUELO_DB_RUNTIME || join(os.tmpdir(), 'manuelo-db-runtime');
const migrationsDir = join(root, 'db', 'migrations');
const testsDir = join(root, 'db', 'tests');
const seedFile = join(root, 'db', 'seed', 'dev_seed.sql');

function listSql(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).filter((f) => f.endsWith('.sql')).sort();
}

function freePort() {
  return new Promise((res, rej) => {
    const srv = net.createServer();
    srv.once('error', rej);
    srv.listen(0, '127.0.0.1', () => {
      const { port } = srv.address();
      srv.close(() => res(port));
    });
  });
}

function loadDriver() {
  const entry = join(runtimeDir, 'node_modules', 'embedded-postgres', 'dist', 'index.js');
  if (!existsSync(entry)) {
    throw new Error(
      `embedded-postgres não encontrado em ${runtimeDir}. ` +
        'Rode scripts/verificar-db.sh para instalar o runtime automaticamente.',
    );
  }
  return import(pathToFileURL(entry).href);
}

const failed = [];
const applied = [];

function report(kind, name, ok, extra = '') {
  const mark = ok ? 'ok  ' : 'FAIL';
  console.log(`[${mark}] ${kind}: ${name}${extra ? ' — ' + extra : ''}`);
}

let pg;
let client;
let dataDir;

async function stopAll() {
  try {
    if (client) await client.end();
  } catch {
    /* ignore */
  }
  try {
    if (pg) await pg.stop();
  } catch {
    /* ignore */
  }
  try {
    if (dataDir) rmSync(dataDir, { recursive: true, force: true });
  } catch {
    /* ignore */
  }
}

async function main() {
  const { default: EmbeddedPostgres } = await loadDriver();
  const port = await freePort();
  dataDir = mkdtempSync(join(os.tmpdir(), 'manuelo-pg-'));

  pg = new EmbeddedPostgres({
    databaseDir: dataDir,
    user: 'postgres',
    password: 'postgres',
    port,
    persistent: false,
    autoRehydrateSymlinks: true,
    onLog: () => {},
    onError: (e) => console.error('[postgres]', e instanceof Error ? e.message : e),
  });

  console.log(`runtime: postgres embutido (porta ${port}, dados em ${dataDir})`);
  await pg.initialise();
  await pg.start();
  await pg.createDatabase('manuelo');

  client = pg.getPgClient('manuelo');
  await client.connect();
  await client.query('SET client_min_messages = warning');

  for (const file of listSql(migrationsDir)) {
    const sql = readFileSync(join(migrationsDir, file), 'utf8');
    try {
      await client.query(sql);
      applied.push(file);
      report('migration', file, true);
    } catch (err) {
      report('migration', file, false, err.message);
      failed.push(`migration ${file}: ${err.message}`);
      break;
    }
  }

  if (failed.length === 0 && existsSync(seedFile)) {
    try {
      await client.query(readFileSync(seedFile, 'utf8'));
      report('seed', 'dev_seed.sql', true);
    } catch (err) {
      report('seed', 'dev_seed.sql', false, err.message);
      failed.push(`seed dev_seed.sql: ${err.message}`);
    }
  }

  for (const file of listSql(testsDir)) {
    const sql = readFileSync(join(testsDir, file), 'utf8');
    try {
      await client.query(sql);
      report('test', file, true);
    } catch (err) {
      report('test', file, false, err.message);
      failed.push(`test ${file}: ${err.message}`);
    }
  }

  console.log(
    `resumo: ${applied.length} migration(ões), ${listSql(testsDir).length} teste(s), ` +
      `${failed.length} falha(s)`,
  );

  await stopAll();
  pg = undefined;
  client = undefined;
  dataDir = undefined;

  if (failed.length > 0) {
    console.error('\nFALHAS:');
    for (const f of failed) console.error(' - ' + f);
    process.exitCode = 1;
  }
}

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, async () => {
    await stopAll();
    process.exit(1);
  });
}

main().catch(async (err) => {
  console.error('erro fatal no runtime:', err);
  await stopAll();
  process.exit(1);
});
