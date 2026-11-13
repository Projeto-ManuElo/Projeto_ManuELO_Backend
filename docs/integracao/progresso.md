# Progresso (memória do loop; a conversa não é memória)

Iteração: 22 | Orçamento: 120 | Runtime: postgres embutido do juiz (cluster temporário; sem Docker/psql na máquina)
Última atualização: A-80/A-81/A-90 concluídos; juiz 8/8 verde; fechando RELATORIO-FINAL

## Entendimento inicial (8 linhas)
1. O pacote ManuElo_Equipe_Completa foi copiado para a raiz: AGENTS.md, opencode.jsonc, .opencode/agents e docs/integracao.
2. O banco (12 migrations + seed fictício + 14 testes SQL) está pronto e verde em ./scripts/verificar-db.sh.
3. O código tem uma integração inicial (T25, b33ff36) com JdbcClient; ela NÃO atende à DEC-I01 (JPA/entidades/AuditorAware) nem ao backlog completo.
4. F0/F1: QA criou e endureceu ./scripts/verificar-integracao.sh; mapeador preencheu mapa.md e problemas.md (18 problemas, 7 bloqueantes).
5. O juiz endurecido reprova o estado atual na etapa 4 por P-001 (sem entidades JPA), P-008 (sem auditoria JPA) e P-002 (sem serviços/@Transactional): código 1, motivo certo.
6. F2: ordem do backlog confirmada; próximos itens são A-02 (persistência JPA base) e A-03 (entidades/repositórios/round-trip).
7. Depois: S1 autenticação/usuários, S2 imóveis/inquilinos/reformas, S3 contratos, S4 movimentações, S5 documentos, S6 home, S7 relatórios, S8 fecho.
8. Estado só vive em docs/integracao; a conversa não é memória.

## Fases
- [x] F0 Base: juiz criado/endurecido (A-01) e estado real auditado
- [x] F1 Mapa: mapa.md completo (auditoria com evidências)
- [x] F2 Plano: backlog conferido e ordenado (S0 -> S8, ordem por dependência mantida)
- [x] S0 Fundação: A-01/A-02/A-03 concluídos; juiz EXIT 0
- [x] F3 Construção: todos os itens do backlog concluídos (ressalvas P-002/P-019/P-021/P-022/P-023/P-026/P-027)
- [x] F4 Fecho: A-90, juiz 8/8 e RELATORIO-FINAL.md

## Item em andamento
Nenhum. Entrega concluída; lacunas de interface aguardam revisão do usuário.

## Log (uma linha por iteração: fase, item, agente, resultado do juiz)
- Iteração 1: pacote da equipe instalado; baseline T25 commitada (b33ff36); F0 despachada (QA + mapeador).
- Iteração 2: F0/F1 — juiz endurecido por P-017; auditoria do mapeador (18 problemas, 7 bloqueantes); juiz EXIT=1 na etapa 4 por P-001/P-002/P-008.
- Iteração 3: F2 — backlog reordenado e A-01 CONCLUIDO; A-02 despachada ao back.
- Iteração 4: A-02 — 14 entidades JPA + AuditorAware + validate; juiz verde com aviso P-002 (DEC-I15); revisor APROVADO.
- Iteração 5: A-03 — 14 repositórios + round-trip por entidade + UPDATE bloqueado; juiz EXIT 0; revisor APROVADO com ressalvas P-019/P-020.
- Iteração 6: A-10 — Spring Security real (UserDetailsService, AuthenticationManager, rotas, gate, sessão, CSRF off); revisão reprovou 6 pontos e correções aplicadas.
- Iteração 7: A-10 re-revisado e APROVADO; teste 10/0/1; juiz 8/8; commit 97d0f72.
- Iteração 8: A-11/A-12 — CadastroUsuariosTest + UsuarioAdminService + /usuarios com perfil; 22 testes verdes.
- Iteração 9: revisão reprovou barreira de COLABORADOR e caminho JDBC; correções aplicadas (serviço exige ADMIN, repo removido).
- Iteração 10: A-11/A-12 re-revisados e APROVADOS; juiz 8/8; commit ad3c2a5.
- Iteração 11: A-13 — token SHA-256/1h/uso único, entrega SMTP/dev, front lê query param; RecuperacaoSenhaTest 7/7; revisão APROVADA com ressalvas.
- Iteração 12: A-13 endurecido (só ATIVO, falha de envio neutra, invalidação de pendentes) e testes 10/10; juiz 8/8; commit 3755325.
- Iteração 13: A-14 — BootstrapAdminTest 3/3 e suíte com 40 testes; juiz 8/8; commit cb06f03.
- Iteração 14: A-20 — filtros SQL + detalhe com contrato vigente; ImoveisFiltrosTest 6/6; revisão APROVADA; juiz 8/8; commit cb9ff61.
- Iteração 15: A-21 — InquilinoCadastroService transacional + front; teste 4/4; config JDBC hack removido; revisão APROVADA com ressalvas.
- Iteração 16: A-21 commit; suíte 50+ testes verdes; juiz 8/8.
- Iteração 17: A-22 — ReformasView com imóvel selecionável + ReformasHistoricoTest 2/2; juiz 8/8.
- Iteração 18: A-30/A-31/A-32 — filtros SQL, cadastro transacional com overlap e pagamento/caução; testes 7/7, 5/5, 4/4.
- Iteração 19: A-40/A-41/A-42/A-43 — serviço financeiro com R03/R04/R07/R09, estorno ADMIN, extrato e exportação; 10/10 + 7/7.
- Iteração 20: A-50/A-51/A-60 — upload/armazenamento, documentos com filtros/download e Home real; UploadDocumentoTest 7/7.
- Iteração 21: A-70/A-71/A-72 — 4 PDFs OpenPDF, persistência/composição, download ADMIN, RelatoriosView e exportar extrato; 7/7.
- Iteração 22: A-15/A-80/A-81/A-90 — AuditoriaTest 4/4, erro global/fuso pt-BR, perfis dev/prod + README, remoção do DemoData legado e botões mortos; juiz 8/8.
