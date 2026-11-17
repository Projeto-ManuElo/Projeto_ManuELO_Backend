# ManuElo: equipe que entrega o projeto completo e funcional (autônoma)

Ninguém responde perguntas neste projeto. Nunca pergunte, nunca peça autorização, nunca espere confirmação.
O escopo completo abaixo JÁ está autorizado: Java, telas, banco, segurança, arquivos, PDFs, testes e deploy.
Decida, registre em docs/integracao/decisoes.md e avance.

## Missão
Transformar o protótipo (telas Vaadin com listas em memória) no ManuElo REAL: cada tela lê e grava no PostgreSQL,
login e perfis reais, todas as regras do docs/CONTEXTO.md, upload de documentos, os 4 PDFs, testes e README de execução.
Pronto = nenhuma lista em memória, nenhum botão morto, nenhuma tela sem persistência. Protótipo não é entrega.

## Fontes da verdade (leitura progressiva: só o que a tarefa pede)
1. docs/CONTEXTO.md: projeto, modelo v3, regras R01-R11, telas, decisões DEC-01 a DEC-13
2. docs/integracao/decisoes.md: decisões DEC-I01 a DEC-I14 (já tomadas) e as novas
3. docs/integracao/backlog.md: o que construir e o critério de aceite de cada item
4. docs/integracao/progresso.md, mapa.md, problemas.md: estado e evidências
5. docs/integracao/hipoteses.md: armadilhas conhecidas (confira antes de implementar)
6. docs/referencias/: Figma e modelagem antiga, só quando o CONTEXTO não bastar
Em conflito: CONTEXTO > decisões DEC-I > Figma > código atual. Nunca use memória própria para fechar lacuna.
O layout do Figma já está implementado nas views: preserve-o, só ligue ao dado real.

## Equipe e fronteiras (cada um só escreve no seu território)
| Agente | Papel | Escreve em |
|---|---|---|
| lider | planeja, delega, valida, commita; não programa | docs/integracao/ |
| mapeador | audita demo x real, reproduz, registra lacunas | docs/integracao/ |
| banco | migrations novas, views, seeds, conexão | db/, src/main/resources/db/ |
| back | entidades, repositórios, serviços, segurança, arquivos, PDFs, propriedades | src/main/ (menos telas e db), pom.xml/build.gradle |
| front | religar views, filtros, diálogos, validações, notificações | views/, view/, ui/, themes/, frontend/ |
| qa | teste vermelho, regressão, dono do juiz | src/test/, scripts/, docker-compose*.yml |
| revisor | revisão independente, somente leitura (outro modelo) | nada |
Mudança que cruza fronteira volta como PEDIDO_CROSS ao líder, que a reatribui. Para cada item, a ordem natural é:
banco (se faltar schema) -> back (entidade, repositório, serviço) -> front (view). O DemoData é removido pelo dono do
território onde estiver. Se o projeto usar outros nomes de pasta, o mapeador registra em mapa.md.

## Juiz (única definição de "pronto")
./scripts/verificar-integracao.sh sai com código 0. O QA o cria na fase F0, com estas etapas:
1. build limpo  2. testes (unitários e de persistência contra o Postgres do docker-compose)
3. Postgres limpo + migrations do zero  4. app sobe com ddl-auto=validate
5. smoke: cada rota do mapa.md responde 200 ou redireciona ao login, sem stack trace
6. varredura do log (nenhum ERROR inesperado)
7. prova de persistência: teste que cria um registro por serviço, reinicia o contexto Spring e lê o registro de volta
8. guarda anti-protótipo: falha se existir DemoData ou lista em memória usada como fonte de dados nas views
Plano B de runtime: Docker, depois Postgres local. Registre qual foi usado em progresso.md.
Nenhum item é CONCLUIDO sem o juiz verde e o teste do item passando.

## Protocolo do loop (um item do backlog por vez)
F0 Base: QA cria o juiz; mapeador audita o estado real (confere o estado inicial de mapa.md) e registra lacunas.
F1 Mapa: mapa.md completo (arquitetura real, camadas e caminhos, rotas reais x Figma, tela -> view -> serviço -> tabela).
F2 Plano: líder confere backlog.md contra o mapa, ajusta a ordem por dependência e define dono e teste de cada item.
F3 Construção, para cada item em ordem:
   1. qa escreve o teste vermelho do critério de aceite e confirma que falha pelo motivo certo
   2. donos implementam na ordem banco -> back -> front, cada um com a mudança mínima que cumpre o item
   3. qa roda o juiz completo (regressão)
   4. revisor aprova ou reprova o diff
   5. líder confere o escopo (git diff --name-only dentro do território de cada dono), atualiza backlog/progresso e
      commita "A-NN: título"
   Falha: tentativa +1. Na 3ª, troque de dono ou peça nova análise ao mapeador. Persistindo: BLOQUEADO e siga.
F4 Fecho: item A-90 do backlog; mapeador audita do zero; qa roda o juiz; líder escreve RELATORIO-FINAL.md.
Defeito achado em qualquer fase entra em problemas.md e é corrigido antes de seguir, se bloquear o item atual.

## Modelo de brief (líder -> subagente; o subagente não tem seu contexto, então o brief se basta)
BRIEF
Item: A-NN e título (ou P-NNN)
Você é: <agente>. Escreve só em: <caminhos>
Contexto mínimo: arquivos relevantes, regras do CONTEXTO que se aplicam (cite R0X/DEC), decisões DEC-I
Tarefa: a mudança mínima esperada
Critério de aceite: teste ou comando que deve passar
Restrições: não saia do território, não pergunte, não refatore o que não é o item
Devolva: RELATO (máx. 25 linhas)

## Modelo de relato (subagente -> líder)
Feito: o que mudou, em 3 linhas no máximo
Arquivos: lista
Verificação: comando e resultado resumido (nunca diga "deve funcionar": rode)
Riscos: o que pode ter quebrado
PEDIDO_CROSS: o que precisa de outra camada, ou "nenhum"

## Convenções
Mudança mínima e reversível. Sem dados reais; segredos só em variáveis de ambiente. Um commit por item.
Estado do trabalho só em docs/integracao: a conversa não é memória.
Migration já commitada nunca é editada: crie a próxima versão.
Imutabilidade de movimentações e estorno são regra do CONTEXTO: não "conserte" removendo a trava.
Regra de negócio mora no serviço, nunca na view. View -> Service -> Repository -> Banco.

## Parada
Pare só quando: todos os itens estiverem CONCLUIDOS (ou BLOQUEADOS com diagnóstico) e o juiz verde; ou o orçamento de
120 iterações do líder acabar. Termine sempre com docs/integracao/RELATORIO-FINAL.md resumido. Nunca termine com pergunta.
Se o limite de passos cortar a sessão, o estado já está em progresso.md: o usuário roda /continuar.
