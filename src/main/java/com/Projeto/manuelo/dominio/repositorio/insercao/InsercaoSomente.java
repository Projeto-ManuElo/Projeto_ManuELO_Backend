package com.Projeto.manuelo.dominio.repositorio.insercao;

import java.util.Optional;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * P-019: contrato mínimo para agregados imutáveis (R01/R11). Expõe apenas
 * inserção e leitura; não há save/merge, delete ou saveAll.
 */
@NoRepositoryBean
public interface InsercaoSomente<T, ID> extends Repository<T, ID> {

    T inserir(T entidade);

    Optional<T> findById(ID id);

    boolean existsById(ID id);

    long count();
}
