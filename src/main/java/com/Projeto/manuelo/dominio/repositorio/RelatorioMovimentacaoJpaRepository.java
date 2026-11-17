package com.Projeto.manuelo.dominio.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Projeto.manuelo.dominio.RelatorioMovimentacao;
import com.Projeto.manuelo.dominio.RelatorioMovimentacaoId;

public interface RelatorioMovimentacaoJpaRepository
        extends JpaRepository<RelatorioMovimentacao, RelatorioMovimentacaoId> {
}
