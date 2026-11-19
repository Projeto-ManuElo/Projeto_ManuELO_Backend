package com.Projeto.manuelo.dominio.repositorio;

import java.util.UUID;

import com.Projeto.manuelo.dominio.Movimentacao;
import com.Projeto.manuelo.dominio.repositorio.insercao.InsercaoSomente;

public interface MovimentacaoJpaRepository extends InsercaoSomente<Movimentacao, UUID> {
}
