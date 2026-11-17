package com.Projeto.manuelo.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Auditoria comum das tabelas que possuem as quatro colunas
 * criado_por/criado_em/atualizado_por/atualizado_em (DEC-I11).
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @CreatedBy
    @Column(name = "criado_por")
    private UUID criadoPor;

    @CreatedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "criado_em", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime criadoEm;

    @LastModifiedBy
    @Column(name = "atualizado_por")
    private UUID atualizadoPor;

    @LastModifiedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "atualizado_em", columnDefinition = "timestamptz")
    private OffsetDateTime atualizadoEm;
}
