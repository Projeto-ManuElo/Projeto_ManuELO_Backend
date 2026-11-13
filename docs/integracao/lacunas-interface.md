# Lacunas de interface — ManuElo (auditoria do mapeador)

Data: 12/11/2026. Escopo: mapear o que ainda falta na interface em relação a `docs/CONTEXTO.md`
(telas, campos, regras) e ao protótipo/Figma, **sem implementar**. Fontes: código atual em
`ui/src/main/java/br/com/manuelo/` e `ui/src/main/java/br/com/manuelo/dados/`, `src/main/java/`,
`docs/integracao/backlog.md`, `docs/integracao/problemas.md`.

Observação importante: parte dos problemas de `problemas.md` já foi corrigida no código e **não**
aparece como lacuna aqui (ex.: filtros SQL em `/imoveis` e `/contratos`, token real de redefinição
de senha, alteração de perfil em `/usuarios`, seleção de imóvel em Reformas). Esta auditoria reflete
o código atual, não o texto histórico do backlog.

## Resumo por severidade

| Severidade | Quantidade | Definição usada |
|---|---:|---|
| ALTO | 25 | Impede fluxo crítico, viola regra R0x/DEC ou botão principal sem ação real |
| MEDIO | 26 | Funcionalidade parcial, campo coletado e descartado, texto/fluxo enganoso |
| BAIXO | 16 | Cosmético, texto, limite operacional ou melhoria de usabilidade |
| **Total** | **67** | |

## Inputs ausentes (campos de formulário que faltam ou são descartados)

- **Valor do aluguel**: existe em `/inquilinos` (novo) e `/contratos` (novo), mas **não existe em
  `/imoveis` (novo/editar)** e **não é carregado nem salvo na edição de `/inquilinos`**.
- **Caução**: há "Caução inicial" no novo contrato/inquilino, mas não há entrada para depósito
  adicional, devolução (`DEVOLUCAO`) ou ajuste da caução atual; a edição de inquilino descarta a caução.
- **Ação judicial**: existe no novo contrato (com descrição obrigatória), mas não há edição posterior
  no detalhe; o detalhe só exibe em modo leitura.
- **Competência**: não há campo no "Pagar o aluguel" (usa o mês corrente) nem na movimentação de
  aluguel (derivada da data); não é possível pagar/registrar competência específica.
- **Forma de pagamento**: combo na movimentação, mas **texto livre** no "Pagar o aluguel".
- **Imóvel**: campo ausente no formulário de `/movimentacao-financeira` (só existe "Contrato", e só
  quando a categoria é Aluguel).
- **Uploads**: contrato do imóvel, contrato do inquilino, recibo da caução, contrato do contrato,
  comprovante da movimentação e arquivo do documento — todos usam um handler vazio e não gravam bytes.
- **Período livre**: `/relatorios` só oferece trimestre fixo de 2026; não há data início/fim.
- **Classificação de documento pendente**: não há ação para informar tipo/data depois; o diálogo exige
  tipo na criação, contrariando R08.
- **Vínculo de documento**: só imóvel/inquilino; faltam contrato e movimentação.
- **Tipo financeiro do documento**: o combo é coletado, mas não entra em `DadosDocumento` nem no filtro.
- **Filtro Entrada/Saída**: ausente no relatório de movimentação (exigido pelo PDF nº 4).
- **Link "Cadastre-se"**: ausente na tela de login.

## Tabela completa de lacunas

### Autenticação e usuários

| ID | Tela/Rota | Lacuna | Evidência | Impacto | Backlog |
|---|---|---|---|---|---|
| L-01 | Login (`""`) | Não há link para `/cadastro`; usuário novo não descobre o cadastro pela tela | `LoginView.java:53` | MEDIO | A-11 |
| L-02 | Login (`""`) | Rota pública `/login` não existe; a tela só responde em `/` | `mapa.md` (rotas reais x Figma); `LoginView.java:18` | BAIXO | sem item |
| L-03 | Recuperar senha | Tela não tem link de volta ao login nem para cadastro | `RecuperarSenhaView.java:36` | BAIXO | A-13 |
| L-04 | Aguardando aprovação | Tela é estática; não consulta se o usuário foi aprovado nem oferece nova tentativa de login | `AprovacaoView.java:14-23` | BAIXO | A-11 |
| L-05 | Redefinir senha | Sem campo para colar/digitar token quando o link não abre; depende exclusivamente da query string | `RedefinirSenhaView.java:40-50` | BAIXO | A-13/A-90 |
| L-56 | Usuários (`/usuarios`) | Cadastro pendente só oferece "Aprovar"; não há reprovar/recusar/inativar pendente | `UsuariosView.java:53-54` | MEDIO | A-12 |
| L-57 | Usuários (`/usuarios`) | Sessão não revalida perfil/inativação; usuário rebaixado mantém a role antiga até novo login | `SessaoUsuario.java:28-55`; `P-022` | MEDIO | A-80 |
| L-58 | Usuários (`/usuarios`) | Alteração de perfil é um toggle sem escolha explícita do novo perfil | `UsuariosView.java:93-100` | BAIXO | A-12 |
| L-59 | Usuários (`/usuarios`) | "Aprovar" executa direto, sem diálogo de confirmação | `UsuariosView.java:69-77` | BAIXO | A-12 |

### Home

| ID | Tela/Rota | Lacuna | Evidência | Impacto | Backlog |
|---|---|---|---|---|---|
| L-06 | Home (`/home`) | Botão "Exportar" só mostra aviso; não gera nem baixa arquivo | `HomeView.java:25` | ALTO | A-43/A-90 |
| L-07 | Home (`/home`) | Subtexto do card "Contratos próximos do vencimento" é fixo ("Aguardando resolução"), não derivado | `HomeView.java:36-37` | BAIXO | A-60 |
| L-08 | Home (`/home`) | Textos errados: "Novo Movimentação", "próximos do vencimentos", "Movimentação recentes" | `HomeView.java:26,36,65` | BAIXO | A-90 |

### Imóveis e reformas

| ID | Tela/Rota | Lacuna | Evidência | Impacto | Backlog |
|---|---|---|---|---|---|
| L-09 | Imóveis (`/imoveis`) | Botão "Exportar" só mostra aviso; sem geração/exportação real | `ImoveisView.java:40` | ALTO | A-90 |
| L-10 | Imóveis (diálogo) | Upload do contrato é no-op: o arquivo não é gravado nem vinculado ao imóvel | `ImovelDialog.java:73`; `UiKit.java:337-339` | ALTO | A-50 |
| L-11 | Imóveis (detalhe) | Detalhe omite cidade/UF/CEP, dia de vencimento, caução e histórico de reformas; não há "+ Novo Registro" na seção | `ImovelDetalheDialog.java:60-89` | MEDIO | A-20/A-22 |
| L-12 | Imóveis (novo/editar) | Não há input de valor do aluguel; o record guarda `aluguelAtual` mas o formulário não o edita | `ImovelDialog.java:17-19,68-74,146-149` | MEDIO | A-20 |
| L-13 | Imóveis (novo/editar) | Status "Alugado"/"Vago" editável manualmente, contrariando DEC-09 (derivado do contrato) | `ImovelDialog.java:26-27,99,120`; `ImovelRepositorio.java:160-179` | MEDIO | A-20 |
| L-14 | Imóveis (filtros) | Filtros de endereço/inquilino não escapam `%`/`_`; a semântica de busca fica imprevisível | `ImovelRepositorio.java:60-74,78-89` | BAIXO | A-20 |
| L-15 | Reformas (`/imoveis/reformas`) | Botão "Exportar" só mostra aviso | `ReformasView.java:50` | MEDIO | A-90 |
| L-16 | Reformas | Registro de reforma não cria movimentação de saída nem preenche `movimentacao_id`, então não reflete no financeiro | `ReformaRepositorio.java:77-86` | MEDIO | A-22 |
| L-17 | Reformas | Cabeçalho da tela usa título "Imóveis" em vez de "Reformas" | `ReformasView.java:52` | BAIXO | A-22 |
| L-18 | Reformas (diálogo) | Valor inválido é convertido em zero e só falha depois, no repositório | `ReformaDialog.java:61-68` | BAIXO | A-22 |

### Inquilinos

| ID | Tela/Rota | Lacuna | Evidência | Impacto | Backlog |
|---|---|---|---|---|---|
| L-19 | Inquilinos | Uploads de contrato e recibo da caução são no-op; o recibo não é vinculado (`reciboCaminho=null`) | `InquilinoDialog.java:75,78`; `InquilinosView.java:111`; `UiKit.java:337-339` | ALTO | A-50/A-21 |
| L-20 | Inquilinos (editar) | Edição não carrega nem salva fiador, imóvel, aluguel, dia de vencimento e caução; campos aparecem vazios e são descartados | `InquilinoDialog.java:105-119`; `InquilinoCadastroService.java:66-90` | ALTO | A-21 |
| L-21 | Inquilinos (filtros) | Filtros continuam em memória, carregando toda a lista | `InquilinosView.java:77-104` | MEDIO | A-21 |
| L-22 | Inquilinos (diálogo) | Texto "Valor do cação" (grafia errada de caução) e "Recibo do cação" | `InquilinoDialog.java:37,63` | BAIXO | A-90 |
| L-23 | Inquilinos (editar) | Valor do aluguel não é populado na edição, embora exista campo no formulário | `InquilinoDialog.java:105-119` | MEDIO | A-21 |

### Contratos e pagamento

| ID | Tela/Rota | Lacuna | Evidência | Impacto | Backlog |
|---|---|---|---|---|---|
| L-24 | Contratos (novo contrato) | Upload do contrato é no-op; `contratoCaminho` chega nulo e o documento não é persistido | `ContratoDialog.java:122,195-199`; `ContratoCadastroService.java` (não usa o caminho) | ALTO | A-31/A-50 |
| L-25 | Contrato (Pagar o aluguel) | Sem campo de competência (usa mês corrente) e forma de pagamento é texto livre, não combo | `PagarAluguelDialog.java:41-44,64-94` | MEDIO | A-32 |
| L-26 | Contrato (Pagar o aluguel) | Regra de pagamento e escrita ficam no repositório, sem serviço e sem `@Transactional`; os dois INSERTs podem divergir | `ContratoRepositorio.java:317-376` | MEDIO | A-32/A-80 |
| L-27 | Contrato (detalhe) | Detalhe não mostra/baixa o documento do contrato, não permite editar contrato nem ação judicial | `ContratoDetalheView.java:99-136` | ALTO | A-31/A-51 |
| L-28 | Contrato (detalhe) | Não existe ação para depósito adicional, devolução ou ajuste de caução | `ContratoDetalheView.java:105-107`; `PagarAluguelDialog.java:45-50` | MEDIO | A-32 |
| L-29 | Contrato (Pagar o aluguel) | Não há controle de competência já paga; o mesmo mês pode ser pago mais de uma vez | `ContratoRepositorio.java:338-361` | MEDIO | A-32 |

### Movimentação financeira

| ID | Tela/Rota | Lacuna | Evidência | Impacto | Backlog |
|---|---|---|---|---|---|
| L-30 | Movimentação (`/movimentacao-financeira`) | "Pesquisar Lançamentos" só mostra aviso; botão ainda fica visível apenas no tipo Saída | `MovimentacaoFinanceiraView.java:39,108` | ALTO | A-41 |
| L-31 | Movimentação | "Exportar extrato" só mostra aviso; não gera nem persiste PDF | `MovimentacaoFinanceiraView.java:56` | ALTO | A-43 |
| L-32 | Movimentação | Extrato não tem filtro de período; carrega os últimos 200 registros | `MovimentacaoFinanceiraView.java:46,84,151` | ALTO | A-41 |
| L-33 | Movimentação | Comprovante é upload no-op e R04 não é exigida: saída sem documento é aceita | `MovimentacaoFinanceiraView.java:68-73`; `MovimentacaoRepositorio.java:125-145` | ALTO | A-40/A-50 |
| L-34 | Movimentação | Formulário não tem campo imóvel e não permite competência explícita | `MovimentacaoFinanceiraView.java:28-35,73`; `MovimentacaoRepositorio.java:109` | MEDIO | A-40 |
| L-35 | Movimentação | Categoria "Caução" em Entrada vira `OUTRAS_ENTRADAS`, sem registro em `caucao_movimentacoes` (viola R07) | `MovimentacaoFinanceiraView.java:100`; `MovimentacaoRepositorio.java:110-113` | ALTO | A-32 |
| L-36 | Movimentação | Não há ação de estorno nem de edição para ADMINISTRADOR (R01/R02) | `MovimentacaoFinanceiraView.java` (sem ação); `MovimentacaoRepositorio` (sem INSERT de ESTORNO) | ALTO | A-42 |
| L-37 | Movimentação | Categoria "Administrativo" não aparece nas saídas, embora exista no modelo | `MovimentacaoFinanceiraView.java:103` | MEDIO | A-40 |
| L-38 | Movimentação | Extrato mostra só data/tipo/categoria/valor; falta forma de pagamento, comprovante e origem | `MovimentacaoFinanceiraView.java:161-173` | MEDIO | A-41 |

### Documentos

| ID | Tela/Rota | Lacuna | Evidência | Impacto | Backlog |
|---|---|---|---|---|---|
| L-39 | Documentos | Upload no-op e metadados fabricados (`application/pdf`, 1024 bytes, caminho falso) | `DocumentoDialog.java:57`; `DocumentoRepositorio.java:79-89` | ALTO | A-50 |
| L-40 | Documentos | Clique no documento só mostra "Visualização de documentos em preparação"; sem download | `DocumentosView.java:97` | ALTO | A-51 |
| L-41 | Documentos | Não existe ação de classificar documento pendente (informar tipo e data) | `DocumentosView.java` (sem ação); `DocumentoDialog.java:75-80` | ALTO | A-51 |
| L-42 | Documentos (filtros) | Filtro "Tipo financeiro" é ignorado no `corresponde`; além disso o campo é coletado e descartado no cadastro | `DocumentosView.java:102-109`; `DocumentoDialog.java:29-30,82-83` | ALTO | A-51 |
| L-43 | Documentos (filtros) | Filtro "Nome" compara com o nome do arquivo, não com o nome do inquilino | `DocumentosView.java:102-106` | MEDIO | A-51 |
| L-44 | Documentos (diálogo) | Vínculo só permite imóvel ou inquilino; faltam contrato e movimentação | `DocumentoDialog.java:32-39`; `DocumentoRepositorio.java:54-62` | MEDIO | A-50 |
| L-45 | Documentos (diálogo) | Tipo é obrigatório na criação, impedindo `pendente_revisao` com tipo/data vazios (R08) | `DocumentoDialog.java:75-80` | MEDIO | A-50 |
| L-46 | Documentos (lista) | Lista os 60 mais recentes sem paginação nem consulta no banco | `DocumentosView.java:34` | BAIXO | A-51 |

### Relatórios

| ID | Tela/Rota | Lacuna | Evidência | Impacto | Backlog |
|---|---|---|---|---|---|
| L-47 | Relatórios (`/relatorios`) | Só existem 3 tipos; falta "Contratos a vencer" (4º PDF) | `RelatoriosView.java:52-53`; `RelatorioRepositorio.java:45-49` | ALTO | A-70 |
| L-48 | Relatórios | Sem período livre: apenas trimestre fixo de 2026 (trimestre deveria ser só atalho) | `RelatoriosView.java:46-49,135-145` | ALTO | A-72 |
| L-49 | Relatórios (PDF) | PDF sem nome da fundação, gerado por, data/hora, código e rodapé "Página X de Y" | `GeradorRelatorio.java:52-84` | ALTO | A-70 |
| L-50 | Relatórios | Não há botão/link para baixar o PDF gerado; a tela apenas exibe o caminho | `RelatoriosView.java:102-123` | ALTO | A-71 |
| L-51 | Relatórios | Rota não é restrita a ADMINISTRADOR; menu aparece para todos | `SecurityConfig.java:51-54`; `MainLayout.java:71-75` | ALTO | A-71/A-80 |
| L-52 | Relatórios | Oferece XLSX, mas o escopo diz que relatórios saem só em PDF | `RelatoriosView.java:50` | BAIXO | A-70 |
| L-53 | Relatórios | "Inadimplência" no lugar de "Contratos atrasados"; sem filtro opcional Entrada/Saída no relatório de movimentação | `RelatoriosView.java:52-53`; `RelatorioRepositorio.java:22-41` | MEDIO | A-70 |
| L-54 | Relatórios | PDF de contratos usa sempre colunas "Data/Descrição/Categoria/Valor", inadequadas para contratos | `GeradorRelatorio.java:70-81` | MEDIO | A-70 |
| L-55 | Relatórios | Não existe "PDF personalizado" previsto no item A-72 | `RelatoriosView.java` (sem ação) | MEDIO | A-72 |

### Transversal

| ID | Tela/Rota | Lacuna | Evidência | Impacto | Backlog |
|---|---|---|---|---|---|
| L-60 | Todas as telas | Componente de upload global usa handler vazio; nenhum arquivo é gravado | `UiKit.java:337-339` | ALTO | A-50 |
| L-61 | Todas as telas | Não há armazenamento real (`ArmazenamentoArquivos`/`MANUELO_STORAGE_DIR`) nem validação real de mime/tamanho no servidor | `grep` por armazenamento vazio; `DocumentoRepositorio.java:79-89` | ALTO | A-50 |
| L-62 | Segurança | Autorização por papel só existe em `/usuarios`; estorno, edição de movimentação e relatórios não são exclusivos do ADMINISTRADOR | `SecurityConfig.java:51-54`; `MainLayout.java:71-75` | ALTO | A-80 |
| L-63 | Todas as telas | Erros são tratados caso a caso nas views; não há tratamento global nem mensagem padronizada | `*View.java` com `try/catch RuntimeException`; sem `@ControllerAdvice` | MEDIO | A-80 |
| L-64 | Projeto | O nome `DemoData` foi mantido para os DTOs de tela, podendo disparar a guarda anti-protótipo | `DemoData.java:9` | BAIXO | A-90 |
| L-65 | Todas as telas | Não há feedback visual de arquivo selecionado/enviado; o usuário não sabe que o upload foi descartado | `UiKit.java:337-355` | MEDIO | A-50 |
| L-66 | Imóveis/Inquilinos/Documentos | Listas sem paginação/limite (exceto documentos, que limita a 60) e sem busca no banco em Inquilinos/Documentos | `ImoveisView.java:102-103`; `InquilinosView.java:36,77-104`; `DocumentosView.java:34` | BAIXO | A-20/A-51 |
| L-67 | Recuperação de senha | A tela "Email enviado" promete e-mail, mas fora do perfil `dev` e sem `spring.mail.host` nenhum e-mail/link é entregue | `EmailEnviadoView.java:21`; `EnvioTokenSenhaImpl.java:48-53` | MEDIO | A-13/A-81 |

## Notas de auditoria

- Rotas cobertas nesta tabela: `/` (login), `/cadastro`, `/aguardando-aprovacao` (`aprovacao`),
  `/recuperar-senha`, `/redefinir-senha`, `/email-enviado`, `/home`, `/imoveis`,
  `/imoveis/reformas`, `/inquilinos`, `/contratos`, `/contratos/detalhe`, "Pagar o aluguel",
  `/movimentacao-financeira`, `/documentos`, `/relatorios`, `/usuarios`.
- Não foram alterados `backlog.md`, `problemas.md`, código, testes nem `db/`.
- A contagem considera apenas lacunas de interface/comportamento observáveis no código atual;
  itens já corrigidos no código (filtros SQL de imóveis/contratos, token de senha, perfil de usuário)
  foram deliberadamente omitidos.
