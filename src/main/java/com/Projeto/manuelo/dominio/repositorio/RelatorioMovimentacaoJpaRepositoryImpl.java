package com.Projeto.manuelo.dominio.repositorio;

import com.Projeto.manuelo.dominio.RelatorioMovimentacao;
import com.Projeto.manuelo.dominio.RelatorioMovimentacaoId;
import com.Projeto.manuelo.dominio.repositorio.insercao.InsercaoSomenteImpl;

public class RelatorioMovimentacaoJpaRepositoryImpl
        extends InsercaoSomenteImpl<RelatorioMovimentacao, RelatorioMovimentacaoId> {

    public RelatorioMovimentacaoJpaRepositoryImpl() {
        super(RelatorioMovimentacao.class);
    }
}
