package com.Projeto.manuelo.dominio.repositorio;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Projeto.manuelo.dominio.CaucaoMovimentacao;

public interface CaucaoMovimentacaoJpaRepository
        extends JpaRepository<CaucaoMovimentacao, UUID> {
}
