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
@Table(name = "contratos")
@EntityListeners(AuditingEntityListener.class)
public class Contrato extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "imovel_id", nullable = false)
    private UUID imovelId;

    @Column(name = "inquilino_id", nullable = false)
    private UUID inquilinoId;

    @Column(name = "fiador_id")
    private UUID fiadorId;

    @Column(name = "inicio", nullable = false)
    private LocalDate inicio;

    @Column(name = "fim")
    private LocalDate fim;

    @Column(name = "valor_aluguel", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorAluguel;

    @Column(name = "dia_vencimento", nullable = false)
    private Short diaVencimento;

    @Column(name = "acompanhar_pagamento_desde", nullable = false)
    private LocalDate acompanharPagamentoDesde;

    @Column(name = "possui_acao_judicial", nullable = false)
    private boolean possuiAcaoJudicial;

    @Column(name = "acao_judicial_descricao", length = 500)
    private String acaoJudicialDescricao;
}
