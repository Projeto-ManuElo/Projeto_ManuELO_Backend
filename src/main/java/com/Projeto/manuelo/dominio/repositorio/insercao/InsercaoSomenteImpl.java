package com.Projeto.manuelo.dominio.repositorio.insercao;

import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Implementação base dos repositórios insert-only. Usa EntityManager.persist
 * para não permitir merge/update acidental em tabelas protegidas por trigger.
 */
public abstract class InsercaoSomenteImpl<T, ID> implements InsercaoSomente<T, ID> {

    @PersistenceContext
    private EntityManager entityManager;

    private final Class<T> tipo;

    protected InsercaoSomenteImpl(Class<T> tipo) {
        this.tipo = tipo;
    }

    @Override
    public T inserir(T entidade) {
        entityManager.persist(entidade);
        return entidade;
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(entityManager.find(tipo, id));
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    @Override
    public long count() {
        return entityManager.createQuery("select count(e) from " + tipo.getSimpleName() + " e",
                Long.class).getSingleResult();
    }
}
