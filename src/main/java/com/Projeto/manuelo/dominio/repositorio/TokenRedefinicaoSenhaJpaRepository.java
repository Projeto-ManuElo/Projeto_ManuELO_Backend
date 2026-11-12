package com.Projeto.manuelo.dominio.repositorio;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Projeto.manuelo.dominio.TokenRedefinicaoSenha;

public interface TokenRedefinicaoSenhaJpaRepository
        extends JpaRepository<TokenRedefinicaoSenha, UUID> {
}
