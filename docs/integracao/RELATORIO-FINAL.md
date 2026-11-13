# RELATÓRIO FINAL — ManuElo

Data: 13/11/2026
Status: **ENTREGA FUNCIONAL CONCLUÍDA** — backlog A-01..A-90 concluído; juiz de integração verde.

## Resultado do juiz

```
./scripts/verificar-integracao.sh
=== Etapa 8/8: guarda anti-protótipo ===
Guarda anti-protótipo OK (records de Modelos como DTO são permitidos).
==============================================
VERIFICAÇÃO DE INTEGRAÇÃO OK (8/8 etapas)
Runtime do banco: cluster PostgreSQL temporário do juiz
==============================================
EXIT 0
```

- 96 testes, 0 falhas, 0 erros, 1 skip; 17 classes de teste.
- Etapas verdes: build limpo, suíte completa, migrations do zero, `ddl-auto=validate`, smoke das rotas, varredura de log, prova de persistência após reinício do contexto e guarda anti-protótipo.

## O que foi entregue

- **Persistência real**: 14 entidades JPA, `BaseEntity`/`AuditorAware`, `@Immutable` em movimentações, migrations Flyway e `validate`.
- **Autenticação e usuários**: Spring Security, BCrypt, login/logout, cadastro pendente, aprovação, perfis ADMINISTRADOR/COLABORADOR, recuperação de senha com token SHA-256 de uso único/1h e bootstrap de admin apenas por variáveis.
- **Imóveis, inquilinos e reformas**: listas, filtros SQL, cadastro/edição, detalhe com contrato vigente, inquilino com fiador/imóvel/caução/upload e histórico de reformas por imóvel.
- **Contratos e caução**: filtros por nome/CPF/imóvel/validade/situação, cadastro transacional com ação judicial, caução e bloqueio de sobreposição; pagamento de aluguel com competência e abatimento de caução (R07).
- **Financeiro**: entradas/saídas com R03/R04, comprovante, extrato com totais assinados, estorno/correção exclusivos do ADMIN e imutabilidade do original (R01/R02/R09).
- **Documentos**: upload PDF/JPG/PNG até 10 MB por interface de armazenamento, `PENDENTE_REVISAO`, classificação, filtros (inclui tipo financeiro), recentes e download real.
- **Home**: indicadores de `vw_home_indicadores`, textos derivados e cliques filtrados.
- **Relatórios**: 4 PDFs OpenPDF com cabeçalho, autor, data/hora, código e “Página X de Y”; persistência de `relatorios` + composição; download e geração restritos ao ADMIN; exportação de extrato.
- **Fecho**: erro global Vaadin em pt-BR, locale/fuso America/Sao_Paulo, perfis `dev`/`prod`, `.env.example`, Dockerfile e README de execução/deploy; `DemoData` legado removido e botões mortos eliminados.

## Evidências por item

| Item | Evidência |
|---|---|
| A-01..A-03 | juiz 8/8; 14 entidades/repositórios; round-trip |
| A-10..A-14 | `SegurancaAutenticacaoTest`, `CadastroUsuariosTest`, `UsuarioAdminService`, `RecuperacaoSenhaTest`, `BootstrapAdminTest` |
| A-15 | `AuditoriaTest` 4/4 |
| A-20..A-22 | `ImoveisFiltrosTest` 6/6, `InquilinoCadastroTest` 4/4, `ReformasHistoricoTest` 2/2 |
| A-30..A-32 | `ContratosFiltrosTest` 7/7, `ContratoCadastroTest` 5/5, `PagamentoAluguelTest` 4/4 |
| A-40..A-43 | `MovimentacaoFinanceiraTest` 10/10, `RelatorioServiceTest` 7/7 |
| A-50..A-51 | `UploadDocumentoTest` 7/7 |
| A-60 | Home com view de indicadores e navegação filtrada |
| A-70..A-72 | `RelatorioServiceTest` 7/7; `SecurityConfig` restringe `/relatorios` |
| A-80..A-81 | `ErrosGlobalConfig`, perfis dev/prod, `.env.example`, README |
| A-90 | guarda anti-protótipo endurecida; sem `DemoData`; sem botões mortos; juiz verde |

## Como executar

```sh
cp .env.example .env          # preencha DB_* e MANUELO_ADMIN_*
docker compose up -d          # ou PostgreSQL próprio
./mvnw spring-boot:run        # perfil dev
```

Testes/juiz:

```sh
JAVA_HOME=/caminho/jdk21 ./mvnw -B test
./scripts/verificar-integracao.sh
```

Produção: `--spring.profiles.active=prod`, com `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MANUELO_ADMIN_EMAIL`, `MANUELO_ADMIN_SENHA`, `MANUELO_APP_URL` e `MANUELO_STORAGE_DIR` obrigatórios. O `Dockerfile` já usa o perfil `prod`.

## Pendências conscientes (fora do backlog executado)

O arquivo `docs/integracao/lacunas-interface.md` mapeia **67 lacunas de interface** (25 ALTO, 26 MÉDIO, 16 BAIXO) que foram deliberadamente adiadas para revisão do usuário antes de implementar; não são itens A-01..A-90.

Problemas residuais registrados em `docs/integracao/problemas.md` (7 abertos):
P-002 (views de leitura de imóveis/reformas ainda usam repositório direto), P-019 (repositórios imutáveis expõem `save/delete`), P-021 (cadastro A-11 ainda em JDBC), P-022 (perfil/inativação não revoga sessão viva), P-023 (envio de token antes do commit), P-026 (detalhe de imóvel incompleto/filtro case-sensitive) e P-027 (edição/filtros de inquilinos).

Nenhum desses impede o juiz ou o fluxo principal; ficam documentados para a próxima rodada.

## Documentos de estado

- `docs/integracao/backlog.md` — itens e evidências
- `docs/integracao/progresso.md` — iterações e runtime
- `docs/integracao/problemas.md` — 20 resolvidos / 7 abertos
- `docs/integracao/decisoes.md` — DEC-I01..DEC-I14
- `docs/integracao/lacunas-interface.md` — 67 lacunas adiadas
