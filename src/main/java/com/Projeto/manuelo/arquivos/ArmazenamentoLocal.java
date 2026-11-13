package com.Projeto.manuelo.arquivos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Armazenamento em sistema de arquivos (DEC-I06). O nome original nunca entra no
 * caminho: cada arquivo recebe um UUID e a extensão derivada do mime validado.
 */
@Component
public class ArmazenamentoLocal implements ArmazenamentoArquivos {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_JPEG = "image/jpeg";
    private static final String MIME_PNG = "image/png";

    private final Path diretorio;

    public ArmazenamentoLocal(
            @Value("${manuelo.storage.dir:${java.io.tmpdir}/manuelo-documentos}") String diretorio) {
        this.diretorio = Paths.get(diretorio).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.diretorio);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Não foi possível criar o diretório de armazenamento " + this.diretorio
                            + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public String salvar(String nomeOriginal, String tipoMime, byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new IllegalStateException("O arquivo está vazio.");
        }

        String extensao = extensao(tipoMime);
        String nome = UUID.randomUUID() + "." + extensao;

        try {
            Files.createDirectories(diretorio);
            Path destino = resolver(nome);
            Files.write(destino, conteudo, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return nome;
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Falha ao salvar o arquivo no diretório de armazenamento: "
                            + ex.getMessage(), ex);
        }
    }

    @Override
    public byte[] ler(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalStateException("Caminho do arquivo não informado.");
        }

        Path alvo = resolver(caminho);
        if (!Files.isRegularFile(alvo)) {
            throw new IllegalStateException("Arquivo não encontrado: " + caminho);
        }

        try {
            return Files.readAllBytes(alvo);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Falha ao ler o arquivo " + caminho + ": " + ex.getMessage(), ex);
        }
    }

    private Path resolver(String caminho) {
        Path alvo = diretorio.resolve(caminho).normalize();
        if (!alvo.startsWith(diretorio)) {
            throw new IllegalStateException(
                    "Caminho de arquivo inválido: fora do diretório de armazenamento.");
        }
        return alvo;
    }

    private String extensao(String tipoMime) {
        if (tipoMime == null || tipoMime.isBlank()) {
            throw new IllegalStateException("Tipo de arquivo não informado.");
        }
        String mime = tipoMime.trim().toLowerCase(Locale.ROOT);
        return switch (mime) {
            case MIME_PDF -> "pdf";
            case MIME_JPEG -> "jpg";
            case MIME_PNG -> "png";
            default -> throw new IllegalStateException(
                    "Tipo de arquivo não suportado: " + tipoMime
                            + ". Use PDF, JPG ou PNG.");
        };
    }
}
