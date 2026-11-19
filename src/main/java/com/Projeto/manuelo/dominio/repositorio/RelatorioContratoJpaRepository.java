package com.Projeto.manuelo.dominio.repositorio;

import com.Projeto.manuelo.dominio.RelatorioContrato;
import com.Projeto.manuelo.dominio.RelatorioContratoId;
import com.Projeto.manuelo.dominio.repositorio.insercao.InsercaoSomente;

public interface RelatorioContratoJpaRepository
        extends InsercaoSomente<RelatorioContrato, RelatorioContratoId> {
}
