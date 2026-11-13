# Como usar (equipe completa do ManuElo)

1. Copie este pacote para a RAIZ do repositório que já tem o app e o banco, mantendo o que já existe
   (db/, docs/CONTEXTO.md, docs/db/, docs/referencias/). Substitua AGENTS.md e opencode.jsonc por estes
   (o agente de banco antigo vira o subagente `banco`). Faça um commit antes.
2. Abra o `opencode` na raiz. O agente padrão é o `lider`. Confira com /models se deepseek-v4.1-flash-free ainda
   está na lista (os free rotacionam); plano B: opencode/glm-5.3-flash-free.
3. Cole o conteúdo de PROMPT-INICIAL.md.
4. O trabalho é grande: o limite de passos vai cortar a sessão várias vezes. Abra outra sessão e rode /continuar.
   O estado fica em docs/integracao/progresso.md e backlog.md. /relatar mostra o resumo a qualquer momento.
5. Ajuste uma vez, se preciso, os caminhos de front e back nos arquivos .opencode/agents/front.md e back.md
   (o padrão supõe pastas chamadas views, view, ui, themes e frontend).

## Limites que você deve conhecer
- O revisor usa outro modelo de propósito (GLM), para enxergar o que o autor não vê.
- As travas de escrita por agente protegem o editor, mas um agente com shell ainda poderia alterar arquivos por
  comando; por isso o líder confere git diff --name-only a cada item.
- Não rodei este pacote no opencode. A primeira execução é o teste.
