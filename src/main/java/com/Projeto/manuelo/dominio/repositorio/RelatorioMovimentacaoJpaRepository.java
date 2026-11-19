package com.Projeto.manuelo.dominio.repositorio;

import com.Projeto.manuelo.dominio.RelatorioMovimentacao;
import com.Projeto.manuelo.dominio.RelatorioMovimentacaoId;
import com.Projeto.manuelo.dominio.repositorio.insercao.InsercaoSomente;

public interface RelatorioMovimentacaoJpaRepository
        extends InsercaoSomente<RelatorioMovimentacao, RelatorioMovimentacaoId> {
}
