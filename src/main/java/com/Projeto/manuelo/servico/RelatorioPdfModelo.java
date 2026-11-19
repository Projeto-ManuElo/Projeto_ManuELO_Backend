package com.Projeto.manuelo.servico;

import java.util.List;

/**
 * Modelo imutável entregue ao renderer. Não contém SQL nem regra de negócio.
 */
final class RelatorioPdfModelo {

    enum Tom {
        NORMAL, POSITIVO, NEGATIVO, ALERTA, MUTED
    }

    record Celula(String texto, Tom tom, boolean negrito) {

        static Celula texto(String texto) {
            return new Celula(texto, Tom.NORMAL, false);
        }

        static Celula negrito(String texto) {
            return new Celula(texto, Tom.NORMAL, true);
        }

        static Celula colorida(String texto, Tom tom, boolean negrito) {
            return new Celula(texto, tom, negrito);
        }
    }

    record Linha(List<Celula> celulas) {

        static Linha de(Celula... celulas) {
            return new Linha(List.of(celulas));
        }
    }

    record Secao(String titulo, String subtitulo, String[] colunas, float[] larguras,
            List<Linha> linhas, List<String> resumo) {
    }

    record Indicador(String rotulo, String valor, Tom tom) {
    }

    record Documento(String titulo, String subtitulo, String codigo, String autor,
            String geradoEm, String referencia, List<Indicador> indicadores,
            List<Secao> secoes, boolean paisagem) {
    }

    private RelatorioPdfModelo() {
    }
}
