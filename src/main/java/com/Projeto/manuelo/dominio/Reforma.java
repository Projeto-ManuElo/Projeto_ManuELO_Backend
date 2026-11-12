package com.Projeto.manuelo.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "reformas")
@EntityListeners(AuditingEntityListener.class)
public class Reforma extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "imovel_id", nullable = false)
    private UUID imovelId;

    @Column(name = "data_reforma", nullable = false)
    private LocalDate dataReforma;

    @Column(name = "descricao", nullable = false, length = 500)
    private String descricao;

    @Column(name = "valor", nullable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    @Column(name = "movimentacao_id")
    private UUID movimentacaoId;
}
