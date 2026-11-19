package com.Projeto.manuelo.dominio.repositorio;

import java.util.UUID;

import com.Projeto.manuelo.dominio.Movimentacao;
import com.Projeto.manuelo.dominio.repositorio.insercao.InsercaoSomenteImpl;

public class MovimentacaoJpaRepositoryImpl
        extends InsercaoSomenteImpl<Movimentacao, UUID> {

    public MovimentacaoJpaRepositoryImpl() {
        super(Movimentacao.class);
    }
}
