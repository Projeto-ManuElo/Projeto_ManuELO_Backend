package com.Projeto.manuelo.dominio;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Immutable
@Entity
@Table(name = "relatorio_movimentacoes")
public class RelatorioMovimentacao {

    @EmbeddedId
    private RelatorioMovimentacaoId id;

    @Column(name = "ordem", nullable = false, updatable = false)
    private Integer ordem;
}
