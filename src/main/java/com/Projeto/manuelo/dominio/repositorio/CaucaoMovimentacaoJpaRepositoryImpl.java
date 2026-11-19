package com.Projeto.manuelo.dominio.repositorio;

import java.util.UUID;

import com.Projeto.manuelo.dominio.CaucaoMovimentacao;
import com.Projeto.manuelo.dominio.repositorio.insercao.InsercaoSomenteImpl;

public class CaucaoMovimentacaoJpaRepositoryImpl
        extends InsercaoSomenteImpl<CaucaoMovimentacao, UUID> {

    public CaucaoMovimentacaoJpaRepositoryImpl() {
        super(CaucaoMovimentacao.class);
    }
}
