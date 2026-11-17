package br.com.manuelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modelos de exibição usados pelas telas. A persistência fica em {@code br.com.manuelo.dados}.
 */
public final class DemoData {

    public record Imovel(String id, String logradouro, String numero, String complemento, String bairro,
            String cidade, String uf, String cep, String tipo, String status, String inquilino,
            BigDecimal aluguel, String observacoes) {

        public String endereco() {
            StringBuilder texto = new StringBuilder();
            texto.append(logradouro == null ? "" : logradouro);
            texto.append(", ").append(numero == null ? "" : numero);
            if (complemento != null && !complemento.isBlank()) {
                texto.append(" - ").append(complemento);
            }
            if (bairro != null && !bairro.isBlank()) {
                texto.append(" - ").append(bairro);
            }
            return texto.toString();
        }
    }

    public record Inquilino(String id, String nome, String cpf, String email, String telefone,
            String endereco, String imovel, String status, BigDecimal caucao) {
    }

    public record Contrato(String id, String inquilino, String cpf, String email,
            LocalDate vencimento, int dias, String endereco, String situacao, BigDecimal aluguel) {
    }

    public record Movimentacao(LocalDate data, String descricao, String categoria, BigDecimal valor) {
    }

    public record Documento(String nome, String meta, String tipo, String situacao) {
    }

    public record Reforma(LocalDate data, String descricao, String proprietario, BigDecimal valor) {
    }

    private DemoData() {
    }
}
