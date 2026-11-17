package com.Projeto.manuelo.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.Projeto.manuelo.dominio.enums.TipoCaucao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Immutable
@Entity
@Table(name = "caucao_movimentacoes")
@EntityListeners(AuditingEntityListener.class)
public class CaucaoMovimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "contrato_id", nullable = false, updatable = false)
    private UUID contratoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", nullable = false, updatable = false, columnDefinition = "tipo_caucao")
    private TipoCaucao tipo;

    @Column(name = "valor", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    @Column(name = "data", nullable = false, updatable = false)
    private LocalDate data;

    @Column(name = "movimentacao_id", updatable = false)
    private UUID movimentacaoId;

    @Column(name = "documento_id", updatable = false)
    private UUID documentoId;

    @CreatedBy
    @Column(name = "criado_por", nullable = false, updatable = false)
    private UUID criadoPor;

    @CreatedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "criado_em", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime criadoEm;
}
