package com.Projeto.manuelo.dominio.repositorio;

import java.util.UUID;

import com.Projeto.manuelo.dominio.Relatorio;
import com.Projeto.manuelo.dominio.repositorio.insercao.InsercaoSomente;

public interface RelatorioJpaRepository extends InsercaoSomente<Relatorio, UUID> {
}
