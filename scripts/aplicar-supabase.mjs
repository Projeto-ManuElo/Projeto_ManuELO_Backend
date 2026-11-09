#!/usr/bin/env node
// Aplica as migrations do ManuElo no Supabase sem psql/Docker (usa node-postgres).
// Uso:
//   ./scripts/aplicar-supabase.sh [--seed] [--admin] [--url <URI>]
// ou:
//   node scripts/aplicar-supabase.mjs [--seed] [--admin] [--url <URI>]
//
// A URI vem de SUPABASE_DB_URL (ou DATABASE_URL/DB_URL) no .env do projeto,
// em ~/.config/manuelo/supabase.env ou do ambiente/CI.
// Requer a dependencia "pg": npm install --prefix scripts

import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import os from 'node:os';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(import.meta.url);

function loadEnvFile(path) {
  if (!existsSync(path)) return;
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const text = line.trim();
    if (!text || text.startsWith('#')) continue;
    const match = text.match(/^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
    if (!match) continue;
    let value = match[2].trim();
    if (
      (value.startsWith("'") && value.endsWith("'")) ||
      (value.startsWith('"') && value.endsWith('"'))
    ) {
      value = value.slice(1, -1);
    }
    if (process.env[match[1]] === undefined) process.env[match[1]] = value;
  }
}

loadEnvFile(join(root, '.env'));
loadEnvFile(
  join(process.env.XDG_CONFIG_HOME || join(os.homedir(), '.config'), 'manuelo', 'supabase.env'),
);

function help() {
  console.log(`Aplica as migrations do ManuElo no Supabase (node-postgres).

Uso:
  ./scripts/aplicar-supabase.sh [opcoes]

Opcoes:
  --seed        aplica db/seed/dev_seed.sql (somente dev/homologacao)
  --admin       cria/aprova o primeiro admin com ADMIN_NOME, ADMIN_EMAIL e ADMIN_HASH
  --url <URI>   informa a URI direto (sobrepoe SUPABASE_DB_URL)
  -h, --help    esta ajuda

A URI deve ser a do Session pooler (porta 5432), com sslmode=require.
Aviso: nao grava flyway_schema_history; nao misture com Flyway no mesmo banco.`);
}

let url = process.env.SUPABASE_DB_URL || process.env.DATABASE_URL || '';
let withSeed = false;
let withAdmin = false;

for (let i = 2; i < process.argv.length; i += 1) {
  const arg = process.argv[i];
  if (arg === '--seed') withSeed = true;
  else if (arg === '--admin') withAdmin = true;
  else if (arg === '--url') {
    i += 1;
    if (i >= process.argv.length) {
      console.error('--url exige uma URI');
      process.exit(2);
    }
    url = process.argv[i];
  } else if (arg === '-h' || arg === '--help') {
    help();
    process.exit(0);
  } else {
    console.error(`opcao desconhecida: ${arg}`);
    process.exit(2);
  }
}

if (!url && process.env.DB_URL) {
  url = process.env.DB_URL.replace(/^jdbc:/, '');
}

if (!url) {
  console.error('Defina SUPABASE_DB_URL no .env (Session pooler, porta 5432).');
  console.error('Modelo: .env.example');
  process.exit(1);
}

let parsed;
try {
  parsed = new URL(url);
} catch {
  console.error('URI invalida. Use postgresql://usuario:senha@host:5432/postgres?sslmode=require');
  process.exit(1);
}

if ((parsed.port || '5432') === '6543') {
  console.error('Use o Session pooler na porta 5432; a porta 6543 e o Transaction pooler.');
  process.exit(1);
}

if (!parsed.username && process.env.DB_USER) parsed.username = process.env.DB_USER;
if (!parsed.password && process.env.DB_PASSWORD) parsed.password = process.env.DB_PASSWORD;

const sslmode = parsed.searchParams.get('sslmode');
parsed.searchParams.delete('sslmode');
const ssl =
  sslmode && sslmode !== 'disable'
    ? { rejectUnauthorized: sslmode === 'verify-full' || sslmode === 'verify-ca' }
    : undefined;

function masked() {
  const copy = new URL(parsed.toString());
  if (copy.password) copy.password = '***';
  return copy.toString();
}

function loadPg() {
  const runtimeDir =
    process.env.MANUELO_DB_RUNTIME || join(os.tmpdir(), 'manuelo-db-runtime');
  const candidates = [
    () => require('pg'),
    () => require(join(runtimeDir, 'node_modules', 'pg')),
  ];
  for (const load of candidates) {
    try {
      return load();
    } catch {
      /* tenta o proximo */
    }
  }
  throw new Error('Dependencia "pg" nao encontrada. Rode: npm install --prefix scripts');
}

function migrationFiles() {
  const dir = join(root, 'db', 'migrations');
  return readdirSync(dir)
    .filter((file) => file.endsWith('.sql'))
    .sort()
    .map((file) => join(dir, file));
}

const { Client } = loadPg();
const client = new Client({ connectionString: parsed.toString(), ssl });

async function runFile(file) {
  const sql = readFileSync(file, 'utf8');
  await client.query('BEGIN');
  try {
    await client.query(sql);
    await client.query('COMMIT');
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    throw error;
  }
}

async function runAdmin() {
  const nome = process.env.ADMIN_NOME;
  const email = process.env.ADMIN_EMAIL;
  const hash = process.env.ADMIN_HASH;
  if (!nome || !email || !hash) {
    throw new Error('Para --admin defina ADMIN_NOME, ADMIN_EMAIL e ADMIN_HASH (bcrypt).');
  }
  await client.query('BEGIN');
  try {
    await client.query(
      `INSERT INTO usuarios (nome, email, senha_hash, perfil, status)
       VALUES ($1, $2, $3, 'ADMINISTRADOR', 'PENDENTE_APROVACAO')
       ON CONFLICT DO NOTHING`,
      [nome, email, hash],
    );
    await client.query(
      `UPDATE usuarios
          SET perfil = 'ADMINISTRADOR',
              status = 'ATIVO',
              aprovado_por = id,
              aprovado_em = now()
        WHERE email = $1 AND status = 'PENDENTE_APROVACAO'`,
      [email],
    );
    await client.query('COMMIT');
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    throw error;
  }
}

try {
  console.log(`Aplicando migrations em ${masked()}`);
  await client.connect();
  await client.query('SET search_path = public, extensions');
  for (const file of migrationFiles()) {
    const name = file.split('/').pop();
    process.stdout.write(`  - ${name}\n`);
    await runFile(file);
  }
  if (withSeed) {
    console.log('Aplicando seed ficticio (dev/homologacao)...');
    await runFile(join(root, 'db', 'seed', 'dev_seed.sql'));
  }
  if (withAdmin) {
    console.log('Criando primeiro administrador...');
    await runAdmin();
  }
  console.log('\nOK. Confira no Supabase (Table Editor ou SQL Editor):');
  console.log('  select count(*) from imoveis;');
  console.log('  select * from vw_home_indicadores;');
} catch (error) {
  console.error(`\nerro: ${error.message}`);
  process.exitCode = 1;
} finally {
  await client.end().catch(() => {});
}
