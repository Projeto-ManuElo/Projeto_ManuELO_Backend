package com.Projeto.manuelo.dominio.repositorio;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Projeto.manuelo.dominio.Reforma;

public interface ReformaJpaRepository extends JpaRepository<Reforma, UUID> {
}
