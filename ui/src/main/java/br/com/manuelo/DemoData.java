package br.com.manuelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class DemoData {

    public record Imovel(String id, String endereco, String tipo, String status,
            String inquilino, BigDecimal aluguel, String observacoes) {
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

    public static List<Imovel> imoveis() {
        List<Imovel> lista = new ArrayList<>();
        lista.add(new Imovel("1", "Rua das Flores, 123 - Centro", "Apartamento", "Alugado",
                "Ana Ferreira", new BigDecimal("1500.00"), "Contrato até 09/02/2026"));
        lista.add(new Imovel("2", "Avenida Brasil, 456 - Jardim América", "Casa", "Alugado",
                "Carlos Silva", new BigDecimal("2200.00"), "Contrato até 15/03/2026"));
        lista.add(new Imovel("3", "Rua das Palmeiras, 789 - Vila Nova", "Comercial", "Vago",
                "", new BigDecimal("1800.00"), "Disponível para locação"));
        lista.add(new Imovel("4", "Rua São João, 321 - Boa Vista", "Apartamento", "Em manutenção",
                "Maria Santos", new BigDecimal("1300.00"), "Reparo hidráulico em andamento"));
        lista.add(new Imovel("5", "Rua Bahia, 654 - Santa Rosa", "Casa", "Alugado",
                "Fernanda Lima", new BigDecimal("1700.00"), "Contrato até 10/04/2026"));
        lista.add(new Imovel("6", "Avenida Paulista, 1000 - Bela Vista", "Comercial", "Alugado",
                "Roberto Alves", new BigDecimal("3500.00"), "Contrato até 05/05/2026"));
        lista.add(new Imovel("7", "Rua Pernambuco, 88 - Centro", "Terreno", "Vago",
                "", new BigDecimal("900.00"), "Terreno murado, pronto para construir"));
        lista.add(new Imovel("8", "Rua Ceará, 210 - Aeroporto", "Apartamento", "Alugado",
                "João Oliveira", new BigDecimal("1250.00"), "Contrato até 20/12/2025"));
        return lista;
    }

    public static List<Inquilino> inquilinos() {
        List<Inquilino> lista = new ArrayList<>();
        lista.add(new Inquilino("1", "Ana Ferreira", "123.456.789-00", "ana.ferreira@email.com",
                "(11) 98765-4321", "Rua das Flores, 123", "Apartamento", "Alugado", new BigDecimal("1500.00")));
        lista.add(new Inquilino("2", "Carlos Silva", "234.567.890-11", "carlos.silva@email.com",
                "(11) 97654-3210", "Avenida Brasil, 456", "Casa", "Alugado", new BigDecimal("2200.00")));
        lista.add(new Inquilino("3", "Maria Santos", "345.678.901-22", "maria.santos@email.com",
                "(11) 96543-2109", "Rua das Palmeiras, 789", "Comercial", "Em manutenção", new BigDecimal("1800.00")));
        lista.add(new Inquilino("4", "João Oliveira", "456.789.012-33", "joao.oliveira@email.com",
                "(11) 95432-1098", "Rua São João, 321", "Apartamento", "Alugado", new BigDecimal("1300.00")));
        lista.add(new Inquilino("5", "Fernanda Lima", "567.890.123-44", "fernanda.lima@email.com",
                "(11) 94321-0987", "Rua Bahia, 654", "Casa", "Alugado", new BigDecimal("1700.00")));
        lista.add(new Inquilino("6", "Roberto Alves", "678.901.234-55", "roberto.alves@email.com",
                "(11) 93210-9876", "Avenida Paulista, 1000", "Comercial", "Alugado", new BigDecimal("3500.00")));
        return lista;
    }

    public static List<Contrato> contratos() {
        List<Contrato> lista = new ArrayList<>();
        lista.add(new Contrato("1", "Ana Ferreira", "123.456.789-00", "ana.ferreira@email.com",
                LocalDate.of(2026, 2, 9), 5, "Rua das Flores, 123", "A vencer", new BigDecimal("1500.00")));
        lista.add(new Contrato("2", "Carlos Silva", "234.567.890-11", "carlos.silva@email.com",
                LocalDate.of(2026, 3, 15), 38, "Avenida Brasil, 456", "Ativo", new BigDecimal("2200.00")));
        lista.add(new Contrato("3", "Maria Santos", "345.678.901-22", "maria.santos@email.com",
                LocalDate.of(2026, 1, 1), -12, "Rua das Palmeiras, 789", "Atrasado", new BigDecimal("1800.00")));
        lista.add(new Contrato("4", "João Oliveira", "456.789.012-33", "joao.oliveira@email.com",
                LocalDate.of(2025, 12, 20), -30, "Rua São João, 321", "Atrasado", new BigDecimal("1300.00")));
        lista.add(new Contrato("5", "Fernanda Lima", "567.890.123-44", "fernanda.lima@email.com",
                LocalDate.of(2026, 4, 10), 64, "Rua Bahia, 654", "Ativo", new BigDecimal("1700.00")));
        lista.add(new Contrato("6", "Roberto Alves", "678.901.234-55", "roberto.alves@email.com",
                LocalDate.of(2026, 5, 5), 89, "Avenida Paulista, 1000", "A vencer", new BigDecimal("3500.00")));
        return lista;
    }

    public static List<Movimentacao> homeMovimentacoes() {
        List<Movimentacao> lista = new ArrayList<>();
        lista.add(new Movimentacao(LocalDate.of(2025, 9, 18), "Aluguel - Apto 302, Ed. Aurora", "Entrada",
                new BigDecimal("1850.00")));
        lista.add(new Movimentacao(LocalDate.of(2025, 9, 17), "Manutenção - reparo hidráulico", "Manutenção",
                new BigDecimal("-420.00")));
        lista.add(new Movimentacao(LocalDate.of(2025, 9, 15), "Aluguel - Sala 12, Ed. Central", "Entrada",
                new BigDecimal("2100.00")));
        for (int i = 0; i < 7; i++) {
            lista.add(new Movimentacao(LocalDate.of(2025, 9, 12), "IPTU - Ed. Aurora", "Tributos",
                    new BigDecimal("-780.00")));
        }
        return lista;
    }

    public static List<Movimentacao> movimentacoes() {
        List<Movimentacao> lista = new ArrayList<>();
        lista.add(new Movimentacao(LocalDate.of(2025, 9, 18), "Aluguel - Apto 302, Ed. Aurora", "Aluguel",
                new BigDecimal("1850.00")));
        lista.add(new Movimentacao(LocalDate.of(2025, 9, 17), "Manutenção - reparo hidráulico", "Manutenção",
                new BigDecimal("-420.00")));
        lista.add(new Movimentacao(LocalDate.of(2025, 9, 12), "IPTU - Ed. Aurora", "Tributos",
                new BigDecimal("-780.00")));
        lista.add(new Movimentacao(LocalDate.of(2025, 9, 12), "IPTU - Ed. Aurora", "Tributos",
                new BigDecimal("-880.00")));
        lista.add(new Movimentacao(LocalDate.of(2025, 9, 12), "IPTU - Ed. Aurora", "Tributos",
                new BigDecimal("-980.00")));
        lista.add(new Movimentacao(LocalDate.of(2025, 9, 12), "IPTU - Ed. Aurora", "Tributos",
                new BigDecimal("-720.00")));
        lista.add(new Movimentacao(LocalDate.of(2025, 9, 12), "IPTU - Ed. Aurora", "Tributos",
                new BigDecimal("-960.00")));
        return lista;
    }

    public static List<Documento> documentos() {
        List<Documento> lista = new ArrayList<>();
        lista.add(new Documento("Contrato - Marina Costa (Ed. Aurora 302)", "Enviado em 03 ago • 1,2 MB", "Contrato", "Concluído"));
        lista.add(new Documento("Recibo - manutenção 17/09", "Enviado em 17 set • 340 KB", "Comprovante", "Concluído"));
        lista.add(new Documento("Cadastro - Estúdio Rocha", "Enviado em 20 set • sem classificação", "Pendente", "Pendente"));
        lista.add(new Documento("Cadastro - Estúdio Rocha", "Enviado em 20 set • sem classificação", "Pendente", "Pendente"));
        lista.add(new Documento("Cadastro - Estúdio Rocha", "Enviado em 20 set • sem classificação", "Pendente", "Pendente"));
        return lista;
    }

    public static List<Reforma> reformas() {
        List<Reforma> lista = new ArrayList<>();
        lista.add(new Reforma(LocalDate.of(2025, 9, 18), "Apto 302, Ed. Aurora - reparo das portas",
                "Leocardi Daniel Oliveira de Oliveira", new BigDecimal("780.00")));
        lista.add(new Reforma(LocalDate.of(2025, 9, 7), "Apto 302, Ed. Aurora - reparo hidráulico",
                "Leocardi Daniel Oliveira de Oliveira", new BigDecimal("420.00")));
        lista.add(new Reforma(LocalDate.of(2025, 8, 20), "Apto 302, Ed. Aurora - reparo na pintura",
                "Leocardi Daniel Oliveira de Oliveira", new BigDecimal("780.00")));
        lista.add(new Reforma(LocalDate.of(2025, 10, 10), "Apto 302, Ed. Aurora - reparo do piso",
                "João Minguel Oliveira de Souza", new BigDecimal("780.00")));
        lista.add(new Reforma(LocalDate.of(2025, 2, 11), "Apto 302, Ed. Aurora - reparo eletrico",
                "João Minguel Oliveira de Souza", new BigDecimal("780.00")));
        lista.add(new Reforma(LocalDate.of(2024, 12, 9), "Apto 302, Ed. Aurora - reparo na pintura",
                "João Minguel Oliveira de Souza", new BigDecimal("780.00")));
        lista.add(new Reforma(LocalDate.of(2024, 11, 15), "Apto 302, Ed. Aurora - reparo das fechaduras",
                "João Minguel Oliveira de Souza", new BigDecimal("780.00")));
        lista.add(new Reforma(LocalDate.of(2024, 7, 12), "Apto 302, Ed. Aurora - reforma hidráulica",
                "João Minguel Oliveira do Santos", new BigDecimal("780.00")));
        lista.add(new Reforma(LocalDate.of(2023, 6, 16), "Apto 302, Ed. Aurora - reparo eletricar",
                "João Minguel Oliveira do Santos", new BigDecimal("780.00")));
        lista.add(new Reforma(LocalDate.of(2023, 3, 1), "Apto 302, Ed. Aurora - reparo do piso",
                "João Minguel Oliveira do Santos", new BigDecimal("780.00")));
        return lista;
    }
}
