package com.Projeto.manuelo.dominio.repositorio;

import java.util.UUID;

import com.Projeto.manuelo.dominio.CaucaoMovimentacao;
import com.Projeto.manuelo.dominio.repositorio.insercao.InsercaoSomente;

public interface CaucaoMovimentacaoJpaRepository
        extends InsercaoSomente<CaucaoMovimentacao, UUID> {
}
