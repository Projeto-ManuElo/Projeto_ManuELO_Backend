package com.Projeto.manuelo.servico;

import java.awt.Color;
import java.io.InputStream;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;

/**
 * Tokens únicos do PDF: cores, fontes e medidas. Mantém o design dos quatro
 * relatórios em um só lugar e garante a fonte embutida no arquivo.
 */
final class RelatorioDesign {

    static final Color NAVY = new Color(0x0F2344);
    static final Color SIDEBAR = new Color(0x161A34);
    static final Color ACCENT = new Color(0x5B4CF5);
    static final Color CREAM = new Color(0xFFF9E9);
    static final Color INK = new Color(0x101828);
    static final Color MUTED = new Color(0x667085);
    static final Color BORDER = new Color(0xEAECF0);
    static final Color ROW_ALT = new Color(0xFBFAF7);
    static final Color SUCCESS = new Color(0x0E9F6E);
    static final Color DANGER = new Color(0xF04438);
    static final Color WARNING = new Color(0xB54708);
    static final Color WHITE = Color.WHITE;

    static final float MARGEM = 36f;
    static final float TOPO = 100f;
    static final float RODAPE = 56f;

    static final BaseFont REGULAR = carregar("/relatorios/fonts/DejaVuSans.ttf",
            BaseFont.HELVETICA);
    static final BaseFont BOLD = carregar("/relatorios/fonts/DejaVuSans-Bold.ttf",
            BaseFont.HELVETICA_BOLD);
    static final BaseFont TITLE = carregar("/relatorios/fonts/DejaVuSerif-Bold.ttf",
            BaseFont.HELVETICA_BOLD);

    static final Font MARCA = fonte(BOLD, 12f, NAVY);
    static final Font MARCA_SUB = fonte(REGULAR, 7.5f, MUTED);
    static final Font TITULO = fonte(TITLE, 18f, NAVY);
    static final Font SUBTITULO = fonte(REGULAR, 9f, MUTED);
    static final Font META = fonte(REGULAR, 7.5f, MUTED);
    static final Font SECAO = fonte(BOLD, 10f, NAVY);
    static final Font SECAO_SUB = fonte(REGULAR, 7.5f, MUTED);
    static final Font CARTAO_LABEL = fonte(BOLD, 7f, MUTED);
    static final Font CARTAO_VALOR = fonte(BOLD, 13f, NAVY);
    static final Font TABELA_HEADER = fonte(BOLD, 7f, WHITE);
    static final Font TABELA_CELULA = fonte(REGULAR, 7f, INK);
    static final Font TABELA_CELULA_BOLD = fonte(BOLD, 7f, INK);
    static final Font RODAPE_TEXTO = fonte(REGULAR, 7f, MUTED);
    static final Font RODAPE_CODIGO = fonte(BOLD, 7f, NAVY);

    private RelatorioDesign() {
    }

    static Font fonte(BaseFont base, float tamanho, Color cor) {
        return new Font(base, tamanho, Font.NORMAL, cor);
    }

    private static BaseFont carregar(String recurso, String fallback) {
        try (InputStream entrada = RelatorioDesign.class.getResourceAsStream(recurso)) {
            if (entrada == null) {
                return BaseFont.createFont(fallback, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            }
            byte[] bytes = entrada.readAllBytes();
            return BaseFont.createFont(recurso, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true,
                    bytes, null);
        } catch (Exception ex) {
            try {
                return BaseFont.createFont(fallback, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception fallbackEx) {
                throw new IllegalStateException("Falha ao carregar a fonte do PDF.", fallbackEx);
            }
        }
    }
}
