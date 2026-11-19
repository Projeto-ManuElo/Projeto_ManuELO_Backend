# Progresso — design dos 4 PDFs geráveis

## FASE A — Diagnóstico e plano

### 1. Diagnóstico do estado atual

- Branch atual: `feature/integracao_front`; working tree limpo antes da criação deste plano (`git status` sem alterações). Este arquivo é o único untracked da missão até o commit do plano.
- O projeto já gera os 4 relatórios em `RelatorioService` com OpenPDF 1.3.43:
  contratos existentes, atrasados, a vencer e movimentação financeira.
- O PDF atual tem: “Fundação Manoel Cruz” (grafia divergente do CONTEXTO, que usa
  “Fundação Manuel Cruz”), título, autor, data/hora, código, rodapé “Página X de Y” e
  tabela cinza.
- Não há logo, cores da identidade, cartões de resumo, subtotais por categoria,
  origem/destino, comprovante sim/não nem seção de estornos.
- `GeradorRelatorio` em `ui/src/main/java/br/com/manuelo/dados` é legado, não é usado
  pela tela e não será base de implementação.
- JasperReports 7.0.8 está no `pom.xml`, mas não há JRXML, integração, data source
  Jasper nem uso atual. OpenPDF já tem testes, fontes, paginação e armazenamento.
- A tela `/relatorios` chama `RelatorioService.gerar` e `baixar`; o fluxo de download
  já está correto. Não há necessidade de alterar a view nesta missão.
- Persistência: `relatorios`, `relatorio_contratos` (situação/dias) e
  `relatorio_movimentacoes` (ids/ordem). O PDF final fica em `ArmazenamentoArquivos`.
- A movimentação não possui `imovel_id` em saídas no modelo v3; o campo existe na
  tabela, mas o serviço de saída não o preenche. Por isso as saídas não terão coluna
  de imóvel; o destino será o favorecido, sem alterar schema.

### 2. Entregáveis

1. **Contratos existentes**: resumo com contratos, vigentes e soma mensal; tabela com
   inquilino, documento, imóvel, início, fim, valor e situação; ordem por nome.
2. **Contratos atrasados**: resumo com quantidade e valor em atraso; tabela com
   inquilino, documento, imóvel, vencimento, dias de atraso e valor; mais atrasados primeiro.
3. **Contratos a vencer**: resumo com quantidade, janela e soma mensal; capa/subtítulo
   “Vencimento em até N dias”; tabela com documento, vencimento (fim), dias restantes e valor;
   vencem primeiro.
4. **Movimentação financeira** (A4 paisagem): 4 cartões (entradas, saídas, saldo,
   registros); subtotais por categoria; tabela de entradas; tabela de saídas (destino = favorecido); seção de estornos quando houver; quadro final
   bruto/estornos/líquido. Filtro opcional Entrada/Saída preservado.

Todos com logo, identidade visual, fonte embutida, cabeçalho, rodapé “Página X de Y”,
código, autor, data/hora, referência/período e estado vazio.

### 3. Decisão de renderização: OpenPDF (exceção já registrada em P-005)

**Item de execução:** A-73 — design e conteúdo dos 4 PDFs. Refs: A-43, A-70, A-71, A-72.

A-73 é um item **local da missão**, registrado apenas neste arquivo porque `docs/integracao/backlog.md` e `docs/integracao/decisoes.md` não estão na lista de arquivos permitidos pelo prompt. Não será criada migration nem alterado schema. A exceção da DEC-I07 está registrada em P-005; se o líder quiser uma DEC-I15 formal, isso é PEDIDO_CROSS fora do escopo desta missão.

- A DEC-I07 prevê Jasper primeiro e OpenPDF após 3 tentativas. O problema **P-005** (`docs/integracao/problemas.md:58-68`) registra a resolução de A-70 com OpenPDF (0/3 tentativas) e fecha o item; esta missão **não reabre** essa decisão, apenas evolui o renderer existente. O revisor deve tratar isso como PEDIDO_CROSS se exigir nova tentativa Jasper.

- **Escolha:** manter OpenPDF e evoluir o renderer atual.
- **Motivo:** já é a tecnologia em produção, com testes e persistência; permite controle
  fino de cabeçalho/rodapé em todas as páginas, fontes embutidas, tabelas repetidas e
  paginação “Página X de Y”. JasperReports 7.0.8 exigiria 4 JRXML novos, compilação,
  data sources, fontes e integração com o Spring Boot 4/Java 21, sem ganho para este
  layout e com risco de regressão. A DEC-I07 prevê OpenPDF como fallback; a decisão será
  registrada como premissa neste arquivo, sem alterar migrations ou schema.
- **Tentativas Jasper:** não serão abertas nesta missão; a exceção está documentada em
  P-005 e a evolução do OpenPDF é a mudança mínima sobre o que já está em produção.
  Se o revisor tratar como bloqueante, o líder abre PEDIDO_CROSS antes da infraestrutura.

### 4. Arquitetura

Fluxo: `RelatorioService` (orquestra, consulta, persiste) → `RelatorioPdfModelo`
(records imutáveis) → `RelatorioPdfRenderer` (OpenPDF) → `ArmazenamentoArquivos`.
O renderer não acessa banco; a montagem de dados não conhece PDF.

Novas classes no pacote `com.Projeto.manuelo.servico`:

- `RelatorioDesign`: tokens únicos de cor, fonte, margem, espaçamento e estilos.
- `RelatorioPdfModelo`: records de indicador, célula, linha, seção e documento.
- `RelatorioDados`: consultas SQL e montagem dos dados de contrato/movimentação.
- `RelatorioPdfRenderer`: cabeçalho, rodapé, cartões, tabelas, totais e paginação.

`RelatorioService` mantém a API pública (`gerar`, `baixar`, `listarRecentes`,
`Arquivo`, `ResumoRelatorio`) e passa a montar os modelos dos 4 PDFs.

### 5. Design

- Cores: `#0F2344` (navy), `#161A34` (sidebar), `#5B4CF5` (accent), `#FFF9E9` (cream),
  `#101828` (ink), `#667085` (muted), `#EAECF0` (border), `#0E9F6E` (positivo),
  `#F04438` (negativo), `#B54708` (alerta).
- Fontes: DejaVu Sans, DejaVu Sans Bold e DejaVu Serif Bold, copiadas do pacote
  `fonts-dejavu-core` do sistema (`/usr/share/fonts/truetype/dejavu`), com a licença
  Bitstream Vera no mesmo diretório `src/main/resources/relatorios/fonts/`. Serão
  embutidas com `BaseFont.IDENTITY_H`; teste falha se o recurso não carregar. São o
  fallback livre para Inter/Playfair Display, que não existem no repositório.
- Logo: `ui/src/main/resources/META-INF/resources/images/logo.png`, carregado do
  classpath; fallback textual se ausente.
- Cabeçalho igual nos 4: logo à esquerda, “Fundação Manuel Cruz · ManuElo” (grafia
  do CONTEXTO) e linha accent. Rodapé: código, “Página X de Y” e data/hora. Autor
  (“Gerado por”) e referência/período ficam no bloco de metadados abaixo do título,
  em todos os PDFs. O código/UI atual usa “Manoel”; alinhar as telas é PEDIDO_CROSS.
- Margens 36 pt; retrato para contratos e paisagem para movimentação; tabelas com
  cabeçalho repetido e quebra de linha; verde/vermelho apenas em valores e etiquetas.

### 6. Fonte dos dados

| PDF | Campo | Origem |
|---|---|---|
| 1 | Inquilino, documento, imóvel, início, fim, valor | `vw_contratos_situacao` |
| 1 | Situação | `situacao_validade`, `atrasado` |
| 1 | Resumo | contagem; vigentes = `inicio <= hoje AND (fim IS NULL OR fim >= hoje)` (inclui A_VENCER e atrasados não expirados); soma mensal = `SUM(valor_aluguel)` desses |
| 2 | Vencimento | `current_date - dias_atraso` (vencimento mais antigo em aberto) |
| 2 | Dias/valor | `dias_atraso`, `valor_em_atraso` |
| 3 | Vencimento/dias | `fim`, `dias_para_vencer` |
| 3 | Janela | `configuracoes.dias_alerta_vencimento` |
| 4 | Entradas/saídas | `movimentacoes` + `usuarios` + `imoveis` + `contratos` + `inquilinos` |
| 4 | Origem | `origem_descricao` ou inquilino + imóvel do contrato |
| 4 | Destino | `favorecido`; saídas não gravam `imovel_id` no serviço atual, então não haverá coluna de imóvel nas saídas |
| 4 | Comprovante | `EXISTS documentos` com `movimentacao_id` e `tipo = COMPROVANTE` |
| 4 | Estornos | `movimentacao_original_id`, `justificativa`, `responsavel_id` |
| 4 | Bruto/estornos/líquido | brutoEntradas=`SUM(valor)` de ENTRADA; brutoSaidas=`SUM(valor)` de SAIDA; estornosEntradas/Saidas=`SUM(valor)` de ESTORNO pelo tipo original; líquido=bruto-estornos; saldoLiquido=liquidoEntradas-liquidoSaidas |

Snapshots: `relatorio_contratos` guarda situação/dias; `relatorio_movimentacoes` guarda
ids/ordem; o arquivo PDF gerado é a cópia imutável baixada depois. Nenhum schema novo.

### 7. Premissas

- Grafia adotada: **Fundação Manuel Cruz** (CONTEXTO, fonte da verdade). O código/UI
  atual usa “Manoel”; a divergência fica registrada como PEDIDO_CROSS para o front,
  sem alterar telas nesta missão.
- Cabeçalho igual nos 4 PDFs; sem assinatura, carimbo, QR Code ou gráficos.
- Saídas não terão coluna de imóvel; o destino é `favorecido`, conforme o modelo v3.
- Fontes DejaVu são o fallback livre; não há Inter/Playfair no repositório.
- O filtro Entrada/Saída afeta as tabelas e os totais exibidos; os cartões continuam
  mostrando o recorte filtrado.
- Fórmulas: bruto = soma dos valores originais (ENTRADA/SAIDA); estornos = soma
  absoluta dos ESTORNO separada pelo tipo original; líquido = bruto - estornos;
  saldo líquido = líquido entradas - líquido saídas. `total_entradas`,
  `total_saidas` e `saldo` persistidos continuam sendo os líquidos, preservando os
  testes atuais.

### 8. Riscos

- Regressão nos 7 testes atuais de `RelatorioServiceTest`; os novos testes usam
  `PdfTextExtractor` (OpenPDF), sem dependência nova.
- Extração de texto de PDF com fonte embutida pode exigir validação por imagem.
- Tabela de movimentação em paisagem tem 8 colunas; risco de aperto visual.
- PDFs longos exigem paginação correta e repetição de cabeçalho.
- `RelatorioService` pode ficar grande; a extração para `RelatorioDados`, modelo e renderer
  reduz o risco.

Critério de pronto: 4 PDFs abrem, texto/acentos corretos, logo/cores/cabeçalho/rodapé,
dados conferem, estado vazio, paginação, testes antigos e novos verdes,
`./scripts/verificar-integracao.sh` com código 0.

### 9. Ordem de execução e commits

1. `docs(relatorios): registra plano de implementação do design dos PDFs`
   - antes do commit: criar branch `feat/pdfs-design`; incluir `docs/integracao/progresso-pdfs.md`
2. `feat(relatorios-pdf): adiciona design tokens, fontes, cabeçalho e rodapé`
3. `feat(relatorios-pdf): aplica novo layout ao relatório de contratos existentes`
4. `feat(relatorios-pdf): aplica novo layout ao relatório de contratos atrasados`
5. `feat(relatorios-pdf): aplica novo layout ao relatório de contratos a vencer`
6. `feat(relatorios-pdf): adiciona subtotais, origem, destino e estornos à movimentação`
7. `test(relatorios-pdf): cobre conteúdo, estado vazio e paginação dos PDFs`
   - usar `com.lowagie.text.pdf.parser.PdfTextExtractor`; conferir cartões, “Documento”,
     “Origem”, “Favorecido”, “Comprovante”, “Estornos”, “Bruto”, “Líquido”, acentos e
     “Página X de Y”; testar ordem por nome e estorno de sinal oposto.
8. `docs(relatorios): registra relato final do design dos PDFs`

Cada passo: compilar, rodar testes do relatório, revisar diff staged e commitar somente
arquivos da missão. Ao final de cada PDF e no fim: `./scripts/verificar-integracao.sh`.

## FASE C — Execução

### 10. Implementação entregue

- `RelatorioDesign`: tokens de cor/fonte/margem e carregamento embutido das fontes DejaVu.
- `RelatorioPdfModelo`: records imutáveis de documento, seção, linha, célula e indicador.
- `RelatorioDados`: consultas de contratos e movimentações; o renderer não acessa banco.
- `RelatorioPdfRenderer`: duas passadas para “Página X de Y”, cabeçalho/rodapé em todas as
  páginas, logo em `/META-INF/resources/images/logo.png`, cartões, tabelas com cabeçalho
  repetido, estado vazio, cores e fontes embutidas.
- `RelatorioService`: mantém a API pública e a persistência insert-only; monta os quatro
  modelos, assina valores de movimentação, separa estornos e persiste totais líquidos.
- Totais persistidos de entradas/saídas são limitados a zero para respeitar
  `chk_relatorios_totais_nao_negativos`; o PDF continua exibindo o líquido real e o saldo
  pode ser negativo quando o estorno é de período anterior.
- Subtotais por categoria respeitam o filtro Entrada/Saída; o relatório completo mostra
  as seis categorias.
- Saídas usam “Favorecido” como destino e não exibem imóvel, pois o serviço de saída não
  grava `imovel_id`; contratos usam o rótulo “Documento”.
- Grafia do PDF: “Fundação Manuel Cruz”. A divergência com “Manoel” nas telas segue como
  PEDIDO_CROSS.

### 11. Testes e evidências

- `RelatorioServiceTest`: 13 testes verdes, incluindo os 7 originais e novos testes de:
  conteúdo/acentos, cartões, subtotais, origem/destino, comprovante, estornos,
  bruto/estornos/líquido, estado vazio, logo, paginação e estorno de período anterior.
- Teste de logo inspeciona o XObject da primeira página; teste de paginação cria 80
  entradas e valida “Página 1 de N” e “Página N de N”.
- `./scripts/verificar-integracao.sh`: **8/8 etapas OK**, 101 testes, 1 skip; build limpo,
  migrations do zero, `ddl-auto=validate`, smoke, log sem ERROR inesperado, prova de
  persistência e guarda anti-protótipo verdes.
- Nenhuma migration/schema novo; nenhuma alteração em `RelatoriosView`.
- PEDIDO_CROSS pendente: formalizar A-73/DEC-I15 em `backlog.md`/`decisoes.md` e alinhar
  a grafia “Manoel” das telas para “Manuel”; ambos fora dos arquivos permitidos.

### 12. Estado dos commits

- Plano: `ea36166 docs(relatorios): registra plano de implementação do design dos PDFs`.
- Implementação e testes serão commitados em seguida, sem push/deploy, conforme a missão.
