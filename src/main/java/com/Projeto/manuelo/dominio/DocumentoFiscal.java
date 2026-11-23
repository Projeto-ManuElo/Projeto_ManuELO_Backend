package com.Projeto.manuelo.dominio;

/**
 * A-96: validação e formatação de CPF/CNPJ sem dependência de UI.
 *
 * <p>O nome é {@code DocumentoFiscal} porque {@link Documento} já é a entidade JPA de
 * arquivos enviados. Valida os dígitos verificadores e recusa sequências repetidas
 * (000..., 111..., ...).</p>
 */
public final class DocumentoFiscal {

    private DocumentoFiscal() {
    }

    public static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    public static String formatar(String valor) {
        String digitos = somenteDigitos(valor);
        if (digitos.length() == 11) {
            return digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "."
                    + digitos.substring(6, 9) + "-" + digitos.substring(9);
        }
        if (digitos.length() == 14) {
            return digitos.substring(0, 2) + "." + digitos.substring(2, 5) + "."
                    + digitos.substring(5, 8) + "/" + digitos.substring(8, 12) + "-"
                    + digitos.substring(12);
        }
        return valor == null ? "" : valor.trim();
    }

    public static boolean cpfValido(String valor) {
        String cpf = somenteDigitos(valor);
        if (cpf.length() != 11 || repetido(cpf)) {
            return false;
        }
        int primeiro = digitoVerificador(cpf, 9, 10);
        int segundo = digitoVerificador(cpf, 10, 11);
        return cpf.charAt(9) - '0' == primeiro && cpf.charAt(10) - '0' == segundo;
    }

    public static boolean cnpjValido(String valor) {
        String cnpj = somenteDigitos(valor);
        if (cnpj.length() != 14 || repetido(cnpj)) {
            return false;
        }
        int primeiro = digitoVerificadorCnpj(cnpj, 12);
        int segundo = digitoVerificadorCnpj(cnpj, 13);
        return cnpj.charAt(12) - '0' == primeiro && cnpj.charAt(13) - '0' == segundo;
    }

    public static boolean valido(String valor) {
        String digitos = somenteDigitos(valor);
        return digitos.length() == 11 ? cpfValido(digitos)
                : digitos.length() == 14 && cnpjValido(digitos);
    }

    private static boolean repetido(String digitos) {
        char primeiro = digitos.charAt(0);
        for (int i = 1; i < digitos.length(); i++) {
            if (digitos.charAt(i) != primeiro) {
                return false;
            }
        }
        return true;
    }

    private static int digitoVerificador(String digitos, int quantidade, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    private static int digitoVerificadorCnpj(String digitos, int quantidade) {
        int[] pesos = quantidade == 12
                ? new int[] { 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 }
                : new int[] { 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
