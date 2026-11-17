package br.com.manuelo.dados;

import java.util.Locale;

/**
 * Traduções entre os valores persistidos (enums do banco) e os rótulos exibidos na interface.
 */
public final class Dominio {

    private Dominio() {
    }

    public static String soDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("[^0-9]", "");
    }

    public static String formatarDocumento(String documento) {
        String d = soDigitos(documento);
        if (d.length() == 11) {
            return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
        }
        if (d.length() == 14) {
            return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/"
                    + d.substring(8, 12) + "-" + d.substring(12);
        }
        return documento == null ? "" : documento;
    }

    public static String formatarTelefone(String telefone) {
        String t = soDigitos(telefone);
        if (t.length() == 11) {
            return "(" + t.substring(0, 2) + ") " + t.substring(2, 7) + "-" + t.substring(7);
        }
        if (t.length() == 10) {
            return "(" + t.substring(0, 2) + ") " + t.substring(2, 6) + "-" + t.substring(6);
        }
        return telefone == null ? "" : telefone;
    }

    public static String iniciais(String nome) {
        if (nome == null || nome.isBlank()) {
            return "?";
        }
        String[] partes = nome.trim().split("\\s+");
        String primeira = partes[0].substring(0, 1);
        String segunda = partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "";
        return (primeira + segunda).toUpperCase(Locale.ROOT);
    }

    public static String tipoImovel(String tipo) {
        return switch (tipo) {
            case "APARTAMENTO" -> "Apartamento";
            case "CASA" -> "Casa";
            case "COMERCIAL" -> "Comercial";
            case "TERRENO" -> "Terreno";
            default -> tipo;
        };
    }

    public static String tipoImovelEnum(String rotulo) {
        if (rotulo == null) {
            return "CASA";
        }
        return switch (rotulo) {
            case "Apartamento" -> "APARTAMENTO";
            case "Casa" -> "CASA";
            case "Comercial" -> "COMERCIAL";
            case "Terreno" -> "TERRENO";
            default -> rotulo;
        };
    }

    public static String statusImovel(String status) {
        return switch (status) {
            case "ALUGADO" -> "Alugado";
            case "VAGO" -> "Vago";
            case "EM_MANUTENCAO" -> "Em manutenção";
            default -> status;
        };
    }

    public static String statusImovelEnum(String rotulo) {
        if (rotulo == null) {
            return "VAGO";
        }
        return switch (rotulo) {
            case "Alugado" -> "ALUGADO";
            case "Vago" -> "VAGO";
            case "Em manutenção" -> "EM_MANUTENCAO";
            default -> rotulo;
        };
    }

    public static String categoria(String valor) {
        if (valor == null) {
            return "";
        }
        return switch (valor) {
            case "ALUGUEL" -> "Aluguel";
            case "OUTRAS_ENTRADAS" -> "Outras entradas";
            case "MANUTENCAO" -> "Manutenção";
            case "TRIBUTOS" -> "Tributos";
            case "ADMINISTRATIVO" -> "Administrativo";
            case "OUTRAS_SAIDAS" -> "Outras saídas";
            case "ESTORNO" -> "Estorno";
            default -> valor;
        };
    }

    public static String formaPagamentoEnum(String rotulo) {
        if (rotulo == null || rotulo.isBlank()) {
            throw new IllegalArgumentException("Informe a forma de pagamento.");
        }
        return switch (rotulo.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "pix" -> "PIX";
            case "boleto" -> "BOLETO";
            case "dinheiro" -> "DINHEIRO";
            case "transferência", "transferencia" -> "TRANSFERENCIA";
            case "caução", "caucao" -> "CAUCAO";
            default -> throw new IllegalArgumentException("Forma de pagamento inválida: " + rotulo);
        };
    }

    public static String formaPagamento(String valor) {
        return switch (valor) {
            case "PIX" -> "Pix";
            case "BOLETO" -> "Boleto";
            case "DINHEIRO" -> "Dinheiro";
            case "TRANSFERENCIA" -> "Transferência";
            case "CAUCAO" -> "Caução";
            default -> valor;
        };
    }

    public static String tipoDocumentoEnum(String rotulo) {
        if (rotulo == null) {
            return null;
        }
        return switch (rotulo) {
            case "Contrato" -> "CONTRATO";
            case "Comprovante" -> "COMPROVANTE";
            case "Laudo" -> "DOCUMENTO_CONTABIL";
            case "Cadastro" -> "CADASTRO";
            case "Nota fiscal" -> "NOTA_FISCAL";
            default -> rotulo;
        };
    }

    public static String tipoDocumento(String valor) {
        if (valor == null) {
            return null;
        }
        return switch (valor) {
            case "CONTRATO" -> "Contrato";
            case "COMPROVANTE" -> "Comprovante";
            case "DOCUMENTO_CONTABIL" -> "Laudo";
            case "CADASTRO" -> "Cadastro";
            case "NOTA_FISCAL" -> "Nota fiscal";
            default -> valor;
        };
    }

    public static String situacaoUsuario(String status) {
        return switch (status) {
            case "PENDENTE_APROVACAO" -> "Pendente";
            case "ATIVO" -> "Ativo";
            case "INATIVO" -> "Inativo";
            default -> status;
        };
    }

    public static String perfil(String perfil) {
        return switch (perfil) {
            case "ADMINISTRADOR" -> "Administrador";
            case "COLABORADOR" -> "Colaborador";
            default -> perfil;
        };
    }

    public static String situacaoContrato(boolean atrasado, String validade) {
        if (atrasado) {
            return "Atrasado";
        }
        if ("A_VENCER".equals(validade)) {
            return "A vencer";
        }
        return "Ativo";
    }
}
