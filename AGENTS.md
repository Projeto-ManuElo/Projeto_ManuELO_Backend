# ManuElo: banco de dados (agente autônomo)

Você trabalha sozinho. Ninguém responde perguntas neste projeto.
Nunca pergunte, nunca peça autorização, nunca espere confirmação. Decida, registre e avance.

## Escopo
Só o banco: migrations (db/migrations/V001__nome.sql, compatíveis com Flyway), constraints, triggers,
views, seed fictício (db/seed), testes (db/tests) e docs (docs/db). Não crie código Java nem telas.
Só altere db/, docs/db/, scripts/ e docker-compose.yml.

## Leitura progressiva (leia só o que a tarefa pede)
1. docs/CONTEXTO.md: fonte da verdade (modelo, regras, decisões DEC-nn, telas)
2. docs/db/progresso.md: estado do loop
3. docs/db/decisoes.md: decisões novas que você tomou
4. docs/referencias/: PDFs do Figma e da modelagem antiga, mais o schema-base.dbml.
   Consulte só se o CONTEXTO não bastar (pdftotext -layout arquivo.pdf -).

## Precedência quando as fontes divergem (a mais alta vence)
1. docs/CONTEXTO.md (inclui as DEC)
2. Telas do Figma (docs/referencias/figma)
3. docs/referencias/schema-base.dbml
4. Documentos de modelagem e stack
Nunca preencha lacuna com memória própria.

## Como decidir sem perguntar
Ambiguidade: escolha pela precedência. Empate: a opção mais simples, mais reversível e mais restritiva
para a integridade (NOT NULL/CHECK), a menos que o Figma mostre o campo como opcional.
Registre em docs/db/decisoes.md como DEC-nn (contexto, escolha, motivo, como reverter) e continue.

## Convenções
snake_case, plural, português. PK uuid (gen_random_uuid()). Dinheiro numeric(14,2). Data e hora: timestamptz.
FKs com ON DELETE RESTRICT. Nada é apagado fisicamente.
Toda tabela editável: criado_por, criado_em, atualizado_por, atualizado_em.
Migration já commitada nunca é editada: crie a próxima versão.
Sem dados reais. Seed só fictício. Segredos fora do repositório.

## Verificação (única definição de pronto)
./scripts/verificar-db.sh sai com código 0. Ele: sobe Postgres limpo, aplica db/migrations em ordem,
roda db/tests/*.sql, derruba e refaz tudo do zero.
Todo CHECK, trigger, constraint e view tem um teste que passa e um que DEVE falhar.
Runtime: docker compose; se indisponível, Postgres local (initdb em pasta temporária); se também
indisponível, PGlite via Node. Registre qual foi usado em progresso.md.
Nunca marque tarefa como concluída sem rodar o script e registrar a saída resumida.

## Ritmo
Uma tarefa por iteração. Um commit por tarefa ("T0X: descrição").
No fim de cada iteração, releia progresso.md; não confie na memória da conversa.
