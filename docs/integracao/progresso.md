# Progresso (memória do loop; a conversa não é memória)

Iteração: 3 | Orçamento: 120 | Runtime: postgres local embutido (porta 55433; sem Docker/psql na máquina)
Última atualização: F2 concluída; A-02 (JPA base) em andamento

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
- [ ] F3 Construção: itens concluídos 1 / 25 (A-01)
- [ ] F4 Fecho: A-90, juiz verde, RELATORIO-FINAL.md

## Item em andamento
A-02 Persistência base (JPA/entidades/AuditorAware/validate) — dono: back; tentativa 1.

## Log (uma linha por iteração: fase, item, agente, resultado do juiz)
- Iteração 1: pacote da equipe instalado; baseline T25 commitada (b33ff36); F0 despachada (QA + mapeador).
- Iteração 2: F0/F1 — juiz endurecido por P-017; auditoria do mapeador (18 problemas, 7 bloqueantes); juiz EXIT=1 na etapa 4 por P-001/P-002/P-008.
- Iteração 3: F2 — backlog reordenado e A-01 CONCLUIDO; A-02 despachada ao back.
