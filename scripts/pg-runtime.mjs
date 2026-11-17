// Sobe um PostgreSQL embutido (plano C do AGENTS.md) e fica em primeiro plano
// até receber SIGTERM/SIGINT. Usado pelo juiz (scripts/verificar-integracao.sh)
// para as etapas que precisam de banco: testes Java, validate e smoke.
//
// Variáveis:
//   MANUELO_DB_RUNTIME  diretório onde embedded-postgres está instalado
//   PG_PORT             porta TCP (0 = escolhe uma livre)
//   PG_DATABASE         banco criado (padrão manuelo)
//   PG_USER/PG_PASSWORD credenciais (padrão postgres/postgres)
//
// Imprime "READY host:porta/banco" quando o banco está aceitando conexões.

import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import net from 'node:net';
import os from 'node:os';

const runtimeDir = process.env.MANUELO_DB_RUNTIME || join(os.tmpdir(), 'manuelo-db-runtime');
const user = process.env.PG_USER || 'postgres';
const password = process.env.PG_PASSWORD || 'postgres';
const database = process.env.PG_DATABASE || 'manuelo';
const requestedPort = Number(process.env.PG_PORT || 0);

const driverEntry = join(runtimeDir, 'node_modules', 'embedded-postgres', 'dist', 'index.js');
if (!existsSync(driverEntry)) {
  console.error(`embedded-postgres não encontrado em ${runtimeDir}. Rode scripts/verificar-db.sh primeiro.`);
  process.exit(1);
}
const { default: EmbeddedPostgres } = await import(pathToFileURL(driverEntry).href);

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

let pg;
let dataDir;

async function stopAll() {
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

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, async () => {
    await stopAll();
    process.exit(0);
  });
}

try {
  const port = requestedPort > 0 ? requestedPort : await freePort();
  dataDir = mkdtempSync(join(os.tmpdir(), 'manuelo-pg-runtime-'));
  pg = new EmbeddedPostgres({
    databaseDir: dataDir,
    user,
    password,
    port,
    persistent: false,
    autoRehydrateSymlinks: true,
    onLog: () => {},
    onError: (e) => console.error('[postgres]', e instanceof Error ? e.message : e),
  });
  await pg.initialise();
  await pg.start();
  await pg.createDatabase(database);
  console.log(`READY 127.0.0.1:${port}/${database}`);
  // Mantém o processo vivo; o juiz encerra por PID.
  setInterval(() => {}, 60_000);
} catch (err) {
  console.error('erro ao iniciar o Postgres embutido:', err instanceof Error ? err.message : err);
  await stopAll();
  process.exit(1);
}
