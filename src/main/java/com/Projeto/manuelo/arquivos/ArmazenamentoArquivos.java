package com.Projeto.manuelo.arquivos;

/**
 * Contrato de armazenamento de arquivos (DEC-I06). O caminho devolvido por
 * {@link #salvar(String, String, byte[])} é relativo ao diretório de armazenamento
 * e é o valor gravado em documentos.caminho_armazenamento.
 */
public interface ArmazenamentoArquivos {

    String salvar(String nomeOriginal, String tipoMime, byte[] conteudo);

    byte[] ler(String caminho);
}
