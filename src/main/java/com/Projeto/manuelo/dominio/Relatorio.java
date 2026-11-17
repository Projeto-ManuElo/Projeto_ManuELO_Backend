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

import com.Projeto.manuelo.dominio.enums.FiltroTipoMovimentacao;
import com.Projeto.manuelo.dominio.enums.TipoRelatorio;

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
@Table(name = "relatorios")
@EntityListeners(AuditingEntityListener.class)
public class Relatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nome", nullable = false, updatable = false, length = 160)
    private String nome;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", nullable = false, updatable = false, columnDefinition = "tipo_relatorio")
    private TipoRelatorio tipo;

    @Column(name = "data_referencia", nullable = false, updatable = false)
    private LocalDate dataReferencia;

    @Column(name = "periodo_inicio", updatable = false)
    private LocalDate periodoInicio;

    @Column(name = "periodo_fim", updatable = false)
    private LocalDate periodoFim;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "filtro_tipo_movimentacao", updatable = false,
            columnDefinition = "filtro_tipo_movimentacao")
    private FiltroTipoMovimentacao filtroTipoMovimentacao;

    @Column(name = "janela_dias", updatable = false)
    private Integer janelaDias;

    @Column(name = "total_entradas", updatable = false, precision = 14, scale = 2)
    private BigDecimal totalEntradas;

    @Column(name = "total_saidas", updatable = false, precision = 14, scale = 2)
    private BigDecimal totalSaidas;

    @Column(name = "saldo", updatable = false, precision = 14, scale = 2)
    private BigDecimal saldo;

    @Column(name = "qtd_registros", nullable = false, updatable = false)
    private Integer qtdRegistros;

    @Column(name = "caminho_arquivo", nullable = false, updatable = false, length = 500)
    private String caminhoArquivo;

    @CreatedBy
    @Column(name = "gerado_por", nullable = false, updatable = false)
    private UUID geradoPor;

    @CreatedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "gerado_em", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime geradoEm;
}
