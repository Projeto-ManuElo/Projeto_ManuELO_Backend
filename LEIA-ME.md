# Como usar este pacote

1. Crie uma pasta (ex.: manuelo-db), copie tudo e rode `git init` e um primeiro commit.
2. Dentro dela: `opencode`. O agente `db` já vem como padrão e o modelo free está no opencode.jsonc.
   Confira com /models se o modelo ainda está na lista (os free rotacionam).
   Plano B de modelo: opencode/glm-5.3-flash-free.
3. Cole o conteúdo de PROMPT-INICIAL.md.
4. Se a sessão acabar, o orçamento de passos estourar ou o contexto encher: abra outra sessão e rode
   /continuar. O estado está em docs/db/progresso.md, não na conversa.
5. Ao final: docs/db/RELATORIO-FINAL.md e docs/db/schema.dbml (cole em dbdiagram.io).
   Copie db/migrations para src/main/resources/db/migration no projeto Spring.

## Reaproveitar o contexto em outros prompts
Salve docs/CONTEXTO.md no repositório de cada frente e comece o prompt assim:

    CONTEXTO: leia docs/CONTEXTO.md (fonte da verdade do ManuElo; em conflito com sua memória, ele vence).
    TAREFA: <o que fazer>. Não faça perguntas; registre suposições no fim da resposta.

Sem repositório (chat comum): cole o conteúdo do CONTEXTO.md no início do prompt.
Entidades JPA, telas Vaadin e templates JasperReports devem ler as seções 5 a 9.
