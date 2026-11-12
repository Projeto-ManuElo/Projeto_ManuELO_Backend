package com.Projeto.manuelo.dominio;

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
@Table(name = "fiadores")
@EntityListeners(AuditingEntityListener.class)
public class Fiador extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @Column(name = "documento", nullable = false, length = 18)
    private String documento;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "telefone", length = 20)
    private String telefone;
}
