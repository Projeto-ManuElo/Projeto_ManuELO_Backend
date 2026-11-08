# Decisões novas tomadas pelo agente (append-only)
As DEC-01 a DEC-13 já estão em docs/CONTEXTO.md. Registre aqui só as novas, a partir de DEC-14.

Modelo de entrada:
## DEC-14 título curto
- Contexto: o que estava ambíguo e onde (arquivo/tela)
- Escolha: o que foi decidido
- Motivo: precedência aplicada ou critério de desempate
- Reverter: como desfazer (migration e arquivos afetados)

## DEC-14 ordem de migrations: documentos antes dos triggers de movimentação
- Contexto: T07 (V006) exige o trigger deferido de comprovante em SAIDA, que consulta a tabela
  `documentos`; T08 (V007) é quem cria `documentos`. A ordem original criaria uma dependência invertida.
- Escolha: V006 cria `documentos` e `reformas` (T08); V007 cria os triggers de integridade das
  movimentações, incluindo o comprovante deferido (T07). T07 continua dependendo de V005.
- Motivo: dependência física real; a precedência do CONTEXTO não define ordem de migrations.
- Reverter: se `documentos` voltar para V007, mover o trigger `trg_movimentacao_exige_comprovante_saida`
  para a migration seguinte e ajustar os testes.


## DEC-15 endereço completo com logradouro, número, bairro e cidade obrigatórios
- Contexto: o CONTEXTO não marca quais campos de endereço são opcionais; o schema-base deixava número e
  bairro nulos.
- Escolha: logradouro, numero, bairro e cidade NOT NULL; complemento e cep opcionais; uf com default 'SE'
  e CHECK de duas letras maiúsculas; cep no formato 00000-000 ou 00000000.
- Motivo: desempate do AGENTS a favor da integridade; endereço sem número/bairro não é utilizável.
- Reverter: nova migration com ALTER TABLE imoveis ALTER COLUMN ... DROP NOT NULL e drop dos CHECKs.

## DEC-16 documento e telefone normalizados em dígitos
- Contexto: CPF/CNPJ aparecem formatados no Figma; a busca por CPF precisa funcionar.
- Escolha: `documento` guarda só dígitos (11 para CPF, 14 para CNPJ) em inquilinos e fiadores;
  `telefone` guarda 10 ou 11 dígitos. A formatação fica na interface.
- Motivo: unicidade e busca determinísticas; CHECK mais restritivo e reversível.
- Reverter: nova migration ampliando o CHECK para aceitar pontuação e ajustando os seeds.
