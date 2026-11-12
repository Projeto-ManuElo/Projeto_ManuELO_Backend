package com.Projeto.manuelo.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.Projeto.manuelo.dominio.enums.CategoriaMovimentacao;
import com.Projeto.manuelo.dominio.enums.FormaPagamento;
import com.Projeto.manuelo.dominio.enums.TipoMovimentacao;

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
@Table(name = "movimentacoes")
@EntityListeners(AuditingEntityListener.class)
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", nullable = false, updatable = false, columnDefinition = "tipo_movimentacao")
    private TipoMovimentacao tipo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "categoria", updatable = false, columnDefinition = "categoria_movimentacao")
    private CategoriaMovimentacao categoria;

    @Column(name = "valor", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_movimentacao", nullable = false, updatable = false)
    private LocalDate dataMovimentacao;

    @Column(name = "descricao", nullable = false, updatable = false, length = 255)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "forma_pagamento", updatable = false, columnDefinition = "forma_pagamento")
    private FormaPagamento formaPagamento;

    @Column(name = "imovel_id", updatable = false)
    private UUID imovelId;

    @Column(name = "responsavel_id", nullable = false, updatable = false)
    private UUID responsavelId;

    @Column(name = "contrato_id", updatable = false)
    private UUID contratoId;

    @Column(name = "competencia", updatable = false)
    private LocalDate competencia;

    @Column(name = "origem_descricao", updatable = false, length = 255)
    private String origemDescricao;

    @Column(name = "favorecido", updatable = false, length = 160)
    private String favorecido;

    @Column(name = "movimentacao_original_id", updatable = false)
    private UUID movimentacaoOriginalId;

    @Column(name = "justificativa", updatable = false, length = 500)
    private String justificativa;

    @Column(name = "corrige_movimentacao_id", updatable = false)
    private UUID corrigeMovimentacaoId;

    @CreatedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "criado_em", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime criadoEm;
}
