package com.Projeto.manuelo.dominio.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Projeto.manuelo.dominio.RelatorioContrato;
import com.Projeto.manuelo.dominio.RelatorioContratoId;

public interface RelatorioContratoJpaRepository
        extends JpaRepository<RelatorioContrato, RelatorioContratoId> {
}
