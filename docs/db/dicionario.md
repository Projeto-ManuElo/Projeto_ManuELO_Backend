# Dicionário de dados — ManuElo (schema v3)

Fonte da verdade: `docs/CONTEXTO.md`. Este documento descreve o que está implementado em
`db/migrations/V001..V011` e validado por `db/tests`. Convenções: `snake_case`, plural, português;
PK `uuid` com `gen_random_uuid()`; dinheiro `numeric(14,2)`; data/hora `timestamptz`; data `date`;
FKs com `ON DELETE RESTRICT`; nada é apagado fisicamente.

## Enums

| Enum | Valores |
|---|---|
| `perfil_usuario` | `ADMINISTRADOR`, `COLABORADOR` |
| `status_usuario` | `PENDENTE_APROVACAO`, `ATIVO`, `INATIVO` |
| `tipo_imovel` | `APARTAMENTO`, `CASA`, `COMERCIAL`, `TERRENO` |
| `status_imovel` | `ALUGADO`, `VAGO`, `EM_MANUTENCAO` |
| `tipo_movimentacao` | `ENTRADA`, `SAIDA`, `ESTORNO` |
| `categoria_movimentacao` | `ALUGUEL`, `OUTRAS_ENTRADAS`, `MANUTENCAO`, `TRIBUTOS`, `ADMINISTRATIVO`, `OUTRAS_SAIDAS` |
| `forma_pagamento` | `PIX`, `BOLETO`, `TRANSFERENCIA`, `DINHEIRO`, `CAUCAO` |
| `tipo_caucao` | `DEPOSITO`, `ABATIMENTO_ALUGUEL`, `DEVOLUCAO` |
| `tipo_documento` | `CONTRATO`, `COMPROVANTE`, `NOTA_FISCAL`, `DOCUMENTO_CONTABIL`, `CADASTRO` |
| `tipo_relatorio` | `CONTRATOS_EXISTENTES`, `CONTRATOS_ATRASADOS`, `CONTRATOS_A_VENCER`, `MOVIMENTACAO` |
| `filtro_tipo_movimentacao` | `ENTRADA`, `SAIDA` |
| `situacao_relatorio_contrato` | `EXISTENTE`, `ATRASADO`, `A_VENCER` |

## Tabelas

### usuarios
Usuários do sistema. Cadastro público nasce `COLABORADOR` + `PENDENTE_APROVACAO`.

| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `id` | uuid | não | `gen_random_uuid()` | PK |
| `nome` | varchar(160) | não | — | não vazio |
| `email` | varchar(160) | não | — | formato de e-mail; único por `lower(email)` |
| `senha_hash` | varchar(255) | não | — | ≥ 20 caracteres (BCrypt) |
| `perfil` | `perfil_usuario` | não | `COLABORADOR` | — |
| `status` | `status_usuario` | não | `PENDENTE_APROVACAO` | `ATIVO` exige `aprovado_em`; `PENDENTE_APROVACAO` exige aprovação nula |
| `aprovado_por` | uuid → usuarios | sim | — | precisa estar junto com `aprovado_em` |
| `aprovado_em` | timestamptz | sim | — | idem |
| `ultimo_acesso_em` | timestamptz | sim | — | — |
| `criado_por` | uuid → usuarios | sim | — | auditoria |
| `criado_em` | timestamptz | não | `now()` | auditoria |
| `atualizado_por` | uuid → usuarios | sim | — | auditoria |
| `atualizado_em` | timestamptz | sim | — | carimbado pelo trigger |

Triggers: `trg_usuarios_atualizado_em` (BEFORE UPDATE).

### tokens_redefinicao_senha
Token de recuperação de senha. Só o hash é armazenado; uso único.

| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `id` | uuid | não | `gen_random_uuid()` | PK |
| `usuario_id` | uuid → usuarios | não | — | — |
| `token_hash` | varchar(255) | não | — | único; ≥ 32 caracteres |
| `expira_em` | timestamptz | não | — | > `criado_em` |
| `usado_em` | timestamptz | sim | — | ≥ `criado_em` |
| `criado_em` | timestamptz | não | `now()` | — |

Trigger `trg_tokens_redefinicao_uso_unico`: bloqueia DELETE e UPDATE de token já usado ou de campos
imutáveis (`usuario_id`, `token_hash`, `expira_em`, `criado_em`), com SQLSTATE `55000`.

### imoveis
Imóveis da fundação. `status` guarda a manutenção; alugado/vago são derivados (DEC-09).

| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `id` | uuid | não | `gen_random_uuid()` | PK |
| `logradouro` | varchar(200) | não | — | não vazio; índice GIN trigram |
| `numero` | varchar(20) | não | — | não vazio (DEC-15) |
| `complemento` | varchar(100) | sim | — | — |
| `bairro` | varchar(100) | não | — | não vazio; índice GIN trigram |
| `cidade` | varchar(100) | não | — | não vazio; índice GIN trigram |
| `uf` | char(2) | não | `SE` | `^[A-Z]{2}$` |
| `cep` | varchar(9) | sim | — | `00000-000` ou `00000000` |
| `tipo` | `tipo_imovel` | não | — | — |
| `status` | `status_imovel` | não | `VAGO` | `EM_MANUTENCAO` tem precedência na exibição |
| `criado_por` / `criado_em` | uuid / timestamptz | sim / não | — / `now()` | auditoria |
| `atualizado_por` / `atualizado_em` | uuid / timestamptz | sim | — | auditoria |

### inquilinos
| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `id` | uuid | não | `gen_random_uuid()` | PK |
| `nome` | varchar(160) | não | — | não vazio; GIN trigram |
| `documento` | varchar(18) | não | — | só dígitos: 11 (CPF) ou 14 (CNPJ); único (DEC-16) |
| `email` | varchar(160) | sim | — | formato de e-mail |
| `criado_por` / `criado_em` | uuid / timestamptz | sim / não | — / `now()` | auditoria |
| `atualizado_por` / `atualizado_em` | uuid / timestamptz | sim | — | auditoria |

### fiadores
| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `id` | uuid | não | `gen_random_uuid()` | PK |
| `nome` | varchar(160) | não | — | não vazio; GIN trigram |
| `documento` | varchar(18) | não | — | só dígitos 11/14; único |
| `email` | varchar(160) | sim | — | formato de e-mail |
| `telefone` | varchar(20) | sim | — | 10 ou 11 dígitos (DEC-16) |
| auditoria | — | — | — | idem demais tabelas |

### contratos
| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `id` | uuid | não | `gen_random_uuid()` | PK |
| `imovel_id` | uuid → imoveis | não | — | — |
| `inquilino_id` | uuid → inquilinos | não | — | — |
| `fiador_id` | uuid → fiadores | sim | — | — |
| `inicio` | date | não | — | — |
| `fim` | date | sim | — | `fim >= inicio` |
| `valor_aluguel` | numeric(14,2) | não | — | > 0 |
| `dia_vencimento` | smallint | não | — | 1..31 (ajustado ao último dia do mês no cálculo) |
| `acompanhar_pagamento_desde` | date | não | — | ≥ `inicio` (DEC-12) |
| `possui_acao_judicial` | boolean | não | `false` | se true, descrição obrigatória |
| `acao_judicial_descricao` | varchar(500) | sim | — | não vazia quando `possui_acao_judicial` |
| auditoria | — | — | — | — |

Constraint `ex_contratos_sem_sobreposicao` (`EXCLUDE USING gist`): um imóvel não pode ter dois
contratos com períodos sobrepostos.

### caucao_movimentacoes
Livro-razão imutável da caução. Depósito não é receita.

| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `id` | uuid | não | `gen_random_uuid()` | PK |
| `contrato_id` | uuid → contratos | não | — | — |
| `tipo` | `tipo_caucao` | não | — | `DEPOSITO`, `ABATIMENTO_ALUGUEL`, `DEVOLUCAO` |
| `valor` | numeric(14,2) | não | — | > 0 |
| `data` | date | não | — | — |
| `movimentacao_id` | uuid → movimentacoes | sim | — | obrigatório no `ABATIMENTO_ALUGUEL` |
| `documento_id` | uuid → documentos | sim | — | recibo da caução |
| `criado_por` | uuid → usuarios | não | — | — |
| `criado_em` | timestamptz | não | `now()` | — |

Regras: `ABATIMENTO_ALUGUEL` exige movimentação `ENTRADA`/`ALUGUEL`/`CAUCAO` do mesmo contrato e
mesmo valor; saldo (`DEPOSITO - ABATIMENTO - DEVOLUCAO`) nunca negativo. Imutável (UPDATE/DELETE →
`55000`).

### movimentacoes
Núcleo financeiro, imutável (R01).

| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `id` | uuid | não | `gen_random_uuid()` | PK |
| `tipo` | `tipo_movimentacao` | não | — | `ENTRADA`, `SAIDA`, `ESTORNO` |
| `categoria` | `categoria_movimentacao` | sim | — | nula no ESTORNO; coerente com o tipo |
| `valor` | numeric(14,2) | não | — | > 0 |
| `data_movimentacao` | date | não | — | — |
| `descricao` | varchar(255) | não | — | não vazia |
| `forma_pagamento` | `forma_pagamento` | sim | — | obrigatória fora do ESTORNO; `CAUCAO` só em ALUGUEL |
| `imovel_id` | uuid → imoveis | sim | — | obrigatório em ALUGUEL e igual ao do contrato |
| `responsavel_id` | uuid → usuarios | não | — | autor |
| `contrato_id` | uuid → contratos | sim | — | obrigatório em ALUGUEL |
| `competencia` | date | sim | — | obrigatória em ALUGUEL; 1º dia do mês |
| `origem_descricao` | varchar(255) | sim | — | obrigatória em `OUTRAS_ENTRADAS` |
| `favorecido` | varchar(160) | sim | — | só em `SAIDA` |
| `movimentacao_original_id` | uuid → movimentacoes | sim | — | único; obrigatório no ESTORNO |
| `justificativa` | varchar(500) | sim | — | obrigatória no ESTORNO |
| `corrige_movimentacao_id` | uuid → movimentacoes | sim | — | só em não-ESTORNO; exige estorno prévio (deferido) |
| `criado_em` | timestamptz | não | `now()` | — |

Triggers: `trg_movimentacoes_imutavel`, `trg_movimentacoes_regras` (BEFORE INSERT),
`trg_movimentacao_correcao`, `trg_movimentacao_comprovante_saida`,
`trg_movimentacao_caucao_vinculada` (constraint triggers deferidos).

### documentos
| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `id` | uuid | não | `gen_random_uuid()` | PK |
| `nome_arquivo` | varchar(200) | não | — | não vazio; GIN trigram |
| `tipo` | `tipo_documento` | sim | — | classificação |
| `data_documento` | date | sim | — | classificação |
| `pendente_revisao` | boolean | não | `true` | se false, exige `tipo` e `data_documento` |
| `caminho_armazenamento` | varchar(500) | não | — | caminho no bucket Supabase |
| `tipo_mime` | varchar(100) | não | — | `application/pdf`, `image/jpeg`, `image/png` |
| `tamanho_bytes` | bigint | não | — | > 0 e ≤ 10 MiB |
| `movimentacao_id` | uuid → movimentacoes | sim | — | vínculo |
| `imovel_id` | uuid → imoveis | sim | — | vínculo |
| `inquilino_id` | uuid → inquilinos | sim | — | vínculo |
| `contrato_id` | uuid → contratos | sim | — | vínculo |
| `enviado_por` | uuid → usuarios | não | — | autor |
| `enviado_em` | timestamptz | não | `now()` | — |
| `atualizado_por` / `atualizado_em` | — | sim | — | auditoria |

Ao menos um dos quatro vínculos é obrigatório (R08).

### reformas
| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `id` | uuid | não | `gen_random_uuid()` | PK |
| `imovel_id` | uuid → imoveis | não | — | — |
| `data_reforma` | date | não | — | — |
| `descricao` | varchar(500) | não | — | não vazia |
| `valor` | numeric(14,2) | não | — | > 0 |
| `movimentacao_id` | uuid → movimentacoes | sim | — | despesa vinculada |
| auditoria | — | — | — | — |

O “responsável do período” é derivado do inquilino do contrato vigente na `data_reforma` (DEC-07).

### relatorios
Metadados do PDF gerado; insert-only (UPDATE/DELETE → `55000`).

| Coluna | Tipo | Nulo | Regra |
|---|---|---|---|
| `id` | uuid | não | PK |
| `nome` | varchar(160) | não | não vazio |
| `tipo` | `tipo_relatorio` | não | — |
| `data_referencia` | date | não | — |
| `periodo_inicio` / `periodo_fim` | date | sim | obrigatórios e ordenados em `MOVIMENTACAO`; proibidos nos demais |
| `filtro_tipo_movimentacao` | enum | sim | só em `MOVIMENTACAO` |
| `janela_dias` | integer | sim | obrigatório (> 0) em `CONTRATOS_A_VENCER`; proibido nos demais |
| `total_entradas` / `total_saidas` / `saldo` | numeric(14,2) | sim | obrigatórios em `MOVIMENTACAO`; não negativos |
| `qtd_registros` | integer | não | ≥ 0 |
| `caminho_arquivo` | varchar(500) | não | não vazio |
| `gerado_por` | uuid → usuarios | não | — |
| `gerado_em` | timestamptz | não | `now()` |

### relatorio_movimentacoes / relatorio_contratos
Composição exata do PDF, insert-only.
- `relatorio_movimentacoes`: PK (`relatorio_id`, `movimentacao_id`), `ordem > 0` e única por relatório.
- `relatorio_contratos`: PK (`relatorio_id`, `contrato_id`), `ordem > 0` e única, `situacao`,
  `dias` obrigatório quando `situacao <> 'EXISTENTE'`.

### configuracoes
| Coluna | Tipo | Nulo | Default | Regra |
|---|---|---|---|---|
| `chave` | varchar(100) | não | — | PK, não vazia |
| `valor` | varchar(500) | não | — | não vazio |
| `descricao` | varchar(255) | sim | — | — |
| `criado_em` | timestamptz | não | `now()` | — |
| `atualizado_por` / `atualizado_em` | — | sim / não | — / `now()` | trigger |

Seeds: `dias_alerta_vencimento = 7`, `dias_atraso_critico = 7`.

## Views

| View | Para que serve | Colunas principais |
|---|---|---|
| `vw_contratos_situacao` | contrato + inquilino + imóvel + validade + atraso | `contrato_id`, `situacao_validade`, `dias_para_vencer`, `atrasado`, `dias_atraso`, `valor_em_atraso`, `qtd_competencias_abertas` |
| `vw_contratos_atrasados` | filtro `atrasado` | mesmas colunas |
| `vw_contratos_a_vencer` | filtro `situacao_validade = 'A_VENCER'` | mesmas colunas |
| `vw_imoveis_situacao` | imóvel + situação derivada + contrato atual | `situacao_contrato`, `em_manutencao`, `status_exibicao`, `contrato_atual_id`, `inquilino_atual_nome` |
| `vw_home_indicadores` | uma linha com os 4 cards + subtítulos | `imoveis_cadastrados`, `imoveis_alugados`, `imoveis_vagos`, `imoveis_em_manutencao`, `contratos_proximos_vencimento`, `contratos_atrasados`, `contratos_atrasados_criticos` |
| `vw_reformas_historico` | reforma + endereço + responsável do período | `responsavel_periodo` |
| `vw_documentos_lista` | documento + inquilino derivado + etiqueta | `etiqueta` (`CONTRATO`/`COMPROVANTE`/`PENDENTE`), `tipo_financeiro` |
| `vw_saldo_caucao` | depósitos, abatimentos, devoluções e saldo | `total_depositos`, `total_abatimentos`, `total_devolucoes`, `saldo` |
| `vw_movimentacoes_recentes` | 20 últimas por `criado_em`/data | `imovel_endereco`, `inquilino_nome` |

## Funções

| Função | Retorno | Uso |
|---|---|---|
| `fn_resumo_periodo(inicio, fim)` | entradas, saídas, saldo, qtd, estornos | totais do extrato/relatório; estorno conta na data do estorno (R09) |
| `fn_extrato_periodo(inicio, fim, filtro?)` | linhas detalhadas | extrato com filtro opcional `ENTRADA`/`SAIDA` |
| `fn_contrato_atraso(contrato, referencia?)` | competência mais antiga, dias, valor, quantidade | cálculo de atraso (R05) |
| `fn_contrato_situacao(contrato, referencia?)` | validade, dias para vencer, atraso | base das views de contrato (R06) |
| `fn_atualizar_atualizado_em()` | trigger | carimba auditoria |
| `fn_bloquear_alteracao()` | trigger | imutabilidade (`55000`) |
| `fn_movimentacao_regras()` | trigger | estorno, correção, imóvel do contrato |
| `fn_movimentacao_correcao()` | constraint trigger | exige estorno prévio |
| `fn_movimentacao_comprovante_saida()` | constraint trigger | comprovante em SAIDA |
| `fn_movimentacao_caucao_vinculada()` | constraint trigger | forma CAUCAO com abatimento |
| `fn_caucao_regras()` | trigger | abatimento coerente e saldo ≥ 0 |
| `fn_token_redefinicao_uso_unico()` | trigger | uso único do token |

## Índices e busca

- Trigram (`pg_trgm` GIN): `imoveis.logradouro`, `imoveis.bairro`, `imoveis.cidade`,
  `inquilinos.nome`, `inquilinos.documento`, `fiadores.nome`, `documentos.nome_arquivo`,
  `usuarios.nome`. Permitem `ILIKE '%termo%'` com bom desempenho.
- Financeiro: `movimentacoes(data_movimentacao)`, `(tipo, categoria)`, `contrato_id`, `imovel_id`,
  `competencia`.
- Contratos: `imovel_id`, `inquilino_id`, `fiador_id`, `fim`, `acompanhar_pagamento_desde`.
- `documentos.enviado_em`, vínculos e `nome_arquivo`.
- `caucao_movimentacoes(contrato_id)`, `(data)`, vínculos.
- Únicos: e-mail case-insensitive, documento de inquilino/fiador, token_hash, estorno por original,
  ordem da composição dos relatórios.

## Segurança e RLS
- RLS ligado em todas as tabelas; política `manuelo_app_all` para a role `manuelo_app`.
- `anon` e `authenticated` não têm acesso às tabelas/funções (a aplicação usa JDBC direto).
- `manuelo_app`: SELECT em tudo; INSERT nas tabelas operacionais; UPDATE só em tabelas editáveis;
  DELETE em nenhuma tabela.
- Imutabilidade de `movimentacoes`, `caucao_movimentacoes`, `relatorios` e composições é garantida por
  trigger, não só por permissão (R11).

## Consultas de referência

```sql
-- Home
SELECT * FROM vw_home_indicadores;

-- Contratos atrasados e a vencer
SELECT * FROM vw_contratos_atrasados;
SELECT * FROM vw_contratos_a_vencer;

-- Busca de imóveis por endereço/bairro/cidade (usa GIN trigram)
SELECT id, logradouro, numero, bairro, cidade, status
  FROM imoveis
 WHERE logradouro ILIKE '%farol%' OR bairro ILIKE '%farol%' OR cidade ILIKE '%aracaju%';

-- Extrato de um período, com filtro opcional
SELECT * FROM fn_extrato_periodo(current_date - 30, current_date);
SELECT * FROM fn_extrato_periodo(current_date - 30, current_date, 'SAIDA');

-- Saldo de caução por contrato
SELECT * FROM vw_saldo_caucao WHERE contrato_id = :contrato;
```
