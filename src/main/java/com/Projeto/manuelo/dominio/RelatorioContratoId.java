package com.Projeto.manuelo.dominio;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class RelatorioContratoId implements Serializable {

    @Column(name = "relatorio_id", nullable = false, updatable = false)
    private UUID relatorioId;

    @Column(name = "contrato_id", nullable = false, updatable = false)
    private UUID contratoId;
}
