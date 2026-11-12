package com.Projeto.manuelo.dominio;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.Projeto.manuelo.dominio.enums.SituacaoRelatorioContrato;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Immutable
@Entity
@Table(name = "relatorio_contratos")
public class RelatorioContrato {

    @EmbeddedId
    private RelatorioContratoId id;

    @Column(name = "ordem", nullable = false, updatable = false)
    private Integer ordem;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "situacao", nullable = false, updatable = false,
            columnDefinition = "situacao_relatorio_contrato")
    private SituacaoRelatorioContrato situacao;

    @Column(name = "dias", updatable = false)
    private Integer dias;
}
