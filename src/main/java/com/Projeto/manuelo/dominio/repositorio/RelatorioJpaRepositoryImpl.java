package com.Projeto.manuelo.dominio.repositorio;

import java.util.UUID;

import com.Projeto.manuelo.dominio.Relatorio;
import com.Projeto.manuelo.dominio.repositorio.insercao.InsercaoSomenteImpl;

public class RelatorioJpaRepositoryImpl extends InsercaoSomenteImpl<Relatorio, UUID> {

    public RelatorioJpaRepositoryImpl() {
        super(Relatorio.class);
    }
}
