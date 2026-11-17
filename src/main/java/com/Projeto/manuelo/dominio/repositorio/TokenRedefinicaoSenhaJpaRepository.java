package com.Projeto.manuelo.dominio.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Projeto.manuelo.dominio.TokenRedefinicaoSenha;

public interface TokenRedefinicaoSenhaJpaRepository
        extends JpaRepository<TokenRedefinicaoSenha, UUID> {

    Optional<TokenRedefinicaoSenha> findByTokenHash(String tokenHash);

    List<TokenRedefinicaoSenha> findByUsuarioIdAndUsadoEmIsNull(UUID usuarioId);
}
