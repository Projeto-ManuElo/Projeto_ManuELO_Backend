package com.Projeto.manuelo.dominio.repositorio;

import com.Projeto.manuelo.dominio.RelatorioContrato;
import com.Projeto.manuelo.dominio.RelatorioContratoId;
import com.Projeto.manuelo.dominio.repositorio.insercao.InsercaoSomenteImpl;

public class RelatorioContratoJpaRepositoryImpl
        extends InsercaoSomenteImpl<RelatorioContrato, RelatorioContratoId> {

    public RelatorioContratoJpaRepositoryImpl() {
        super(RelatorioContrato.class);
    }
}
