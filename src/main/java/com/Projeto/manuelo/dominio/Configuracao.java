package com.Projeto.manuelo.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "configuracoes")
@EntityListeners(AuditingEntityListener.class)
public class Configuracao {

    @Id
    @Column(name = "chave", nullable = false, updatable = false, length = 100)
    private String chave;

    @Column(name = "valor", nullable = false, length = 500)
    private String valor;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @CreatedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "criado_em", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime criadoEm;

    @LastModifiedBy
    @Column(name = "atualizado_por")
    private UUID atualizadoPor;

    @LastModifiedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "atualizado_em", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime atualizadoEm;
}
