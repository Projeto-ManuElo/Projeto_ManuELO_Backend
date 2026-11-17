package com.Projeto.manuelo.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.Projeto.manuelo.dominio.enums.TipoDocumento;

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
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "documentos")
@EntityListeners(AuditingEntityListener.class)
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nome_arquivo", nullable = false, length = 200)
    private String nomeArquivo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo", columnDefinition = "tipo_documento")
    private TipoDocumento tipo;

    @Column(name = "data_documento")
    private LocalDate dataDocumento;

    @Column(name = "pendente_revisao", nullable = false)
    private boolean pendenteRevisao;

    @Column(name = "caminho_armazenamento", nullable = false, length = 500)
    private String caminhoArmazenamento;

    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;

    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    @Column(name = "movimentacao_id")
    private UUID movimentacaoId;

    @Column(name = "imovel_id")
    private UUID imovelId;

    @Column(name = "inquilino_id")
    private UUID inquilinoId;

    @Column(name = "contrato_id")
    private UUID contratoId;

    @CreatedBy
    @Column(name = "enviado_por", nullable = false, updatable = false)
    private UUID enviadoPor;

    @CreatedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "enviado_em", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime enviadoEm;

    @LastModifiedBy
    @Column(name = "atualizado_por")
    private UUID atualizadoPor;

    @LastModifiedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "atualizado_em", columnDefinition = "timestamptz")
    private OffsetDateTime atualizadoEm;
}
