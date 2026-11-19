package com.Projeto.manuelo.servico;

import static com.Projeto.manuelo.servico.RelatorioDesign.ACCENT;
import static com.Projeto.manuelo.servico.RelatorioDesign.WARNING;
import static com.Projeto.manuelo.servico.RelatorioDesign.BORDER;
import static com.Projeto.manuelo.servico.RelatorioDesign.BOLD;
import static com.Projeto.manuelo.servico.RelatorioDesign.CARTAO_LABEL;
import static com.Projeto.manuelo.servico.RelatorioDesign.CREAM;
import static com.Projeto.manuelo.servico.RelatorioDesign.DANGER;
import static com.Projeto.manuelo.servico.RelatorioDesign.INK;
import static com.Projeto.manuelo.servico.RelatorioDesign.MARCA;
import static com.Projeto.manuelo.servico.RelatorioDesign.MARCA_SUB;
import static com.Projeto.manuelo.servico.RelatorioDesign.MARGEM;
import static com.Projeto.manuelo.servico.RelatorioDesign.META;
import static com.Projeto.manuelo.servico.RelatorioDesign.MUTED;
import static com.Projeto.manuelo.servico.RelatorioDesign.NAVY;
import static com.Projeto.manuelo.servico.RelatorioDesign.REGULAR;
import static com.Projeto.manuelo.servico.RelatorioDesign.RODAPE;
import static com.Projeto.manuelo.servico.RelatorioDesign.RODAPE_CODIGO;
import static com.Projeto.manuelo.servico.RelatorioDesign.RODAPE_TEXTO;
import static com.Projeto.manuelo.servico.RelatorioDesign.ROW_ALT;
import static com.Projeto.manuelo.servico.RelatorioDesign.SECAO;
import static com.Projeto.manuelo.servico.RelatorioDesign.SECAO_SUB;
import static com.Projeto.manuelo.servico.RelatorioDesign.SUCCESS;
import static com.Projeto.manuelo.servico.RelatorioDesign.TABELA_CELULA_BOLD;
import static com.Projeto.manuelo.servico.RelatorioDesign.TABELA_HEADER;
import static com.Projeto.manuelo.servico.RelatorioDesign.TITULO;
import static com.Projeto.manuelo.servico.RelatorioDesign.TOPO;
import static com.Projeto.manuelo.servico.RelatorioDesign.WHITE;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import com.Projeto.manuelo.servico.RelatorioPdfModelo.Celula;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Documento;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Indicador;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Linha;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Secao;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Tom;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Renderer OpenPDF dos quatro relatórios. Recebe um modelo imutável e não acessa
 * banco; o cabeçalho e o rodapé são desenhados em todas as páginas.
 */
final class RelatorioPdfRenderer {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final byte[] LOGO = carregarLogo();

    record PdfGerado(byte[] conteudo, int paginas) {
    }

    PdfGerado renderizar(Documento documento) {
        int paginas = renderizar(documento, 0).paginas();
        return renderizar(documento, paginas);
    }

    private PdfGerado renderizar(Documento dados, int totalPaginas) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(
                dados.paisagem() ? PageSize.A4.rotate() : PageSize.A4,
                MARGEM, MARGEM, TOPO, RODAPE);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, saida);
            escritor.setPageEvent(new Moldura(totalPaginas, dados.codigo(), dados.geradoEm()));
            documento.open();

            Paragraph titulo = new Paragraph(dados.titulo(), TITULO);
            titulo.setSpacingAfter(2f);
            documento.add(titulo);

            if (dados.subtitulo() != null && !dados.subtitulo().isBlank()) {
                Paragraph subtitulo = new Paragraph(dados.subtitulo(), SECAO_SUB);
                subtitulo.setSpacingAfter(4f);
                documento.add(subtitulo);
            }

            Paragraph meta = new Paragraph("Gerado por: " + dados.autor()
                    + "    Data e hora: " + dados.geradoEm()
                    + "    Código: " + dados.codigo(), META);
            documento.add(meta);

            if (dados.referencia() != null && !dados.referencia().isBlank()) {
                Paragraph referencia = new Paragraph(dados.referencia(), META);
                referencia.setSpacingAfter(6f);
                documento.add(referencia);
            }

            if (!dados.indicadores().isEmpty()) {
                documento.add(cartoes(dados.indicadores()));
            }

            for (Secao secao : dados.secoes()) {
                adicionarSecao(documento, secao);
            }

            int paginas = escritor.getPageNumber();
            documento.close();
            return new PdfGerado(saida.toByteArray(), paginas);
        } catch (Exception ex) {
            if (documento.isOpen()) {
                documento.close();
            }
            throw new IllegalStateException(
                    "Falha ao gerar o PDF do relatório: " + ex.getMessage(), ex);
        }
    }

    private PdfPTable cartoes(List<Indicador> indicadores) throws Exception {
        PdfPTable cartoes = new PdfPTable(indicadores.size());
        cartoes.setWidthPercentage(100);
        cartoes.setSpacingBefore(6f);
        cartoes.setSpacingAfter(10f);
        for (Indicador indicador : indicadores) {
            PdfPCell celula = new PdfPCell();
            celula.setBackgroundColor(CREAM);
            celula.setBorderColor(BORDER);
            celula.setBorderWidth(0.6f);
            celula.setPadding(8f);
            Paragraph rotulo = new Paragraph(indicador.rotulo().toUpperCase(BR), CARTAO_LABEL);
            rotulo.setSpacingAfter(3f);
            celula.addElement(rotulo);
            celula.addElement(new Paragraph(indicador.valor(), valor(indicador.tom())));
            cartoes.addCell(celula);
        }
        return cartoes;
    }

    private void adicionarSecao(Document documento, Secao secao) throws Exception {
        if (secao.titulo() != null && !secao.titulo().isBlank()) {
            Paragraph titulo = new Paragraph(secao.titulo(), SECAO);
            titulo.setSpacingBefore(8f);
            titulo.setSpacingAfter(2f);
            documento.add(titulo);
        }
        if (secao.subtitulo() != null && !secao.subtitulo().isBlank()) {
            Paragraph subtitulo = new Paragraph(secao.subtitulo(), SECAO_SUB);
            subtitulo.setSpacingAfter(4f);
            documento.add(subtitulo);
        }
        documento.add(tabela(secao));
        for (String resumo : secao.resumo()) {
            Paragraph linha = new Paragraph(resumo, TABELA_CELULA_BOLD);
            linha.setAlignment(Element.ALIGN_RIGHT);
            linha.setSpacingBefore(2f);
            documento.add(linha);
        }
    }

    private PdfPTable tabela(Secao secao) throws Exception {
        PdfPTable tabela = new PdfPTable(secao.larguras());
        tabela.setWidthPercentage(100);
        tabela.setHeaderRows(1);
        tabela.setSplitRows(true);
        tabela.setSplitLate(false);
        tabela.setSpacingAfter(2f);

        for (String coluna : secao.colunas()) {
            PdfPCell cabecalho = new PdfPCell(new Phrase(coluna, TABELA_HEADER));
            cabecalho.setBackgroundColor(NAVY);
            cabecalho.setBorderColor(NAVY);
            cabecalho.setPadding(4f);
            cabecalho.setVerticalAlignment(Element.ALIGN_MIDDLE);
            tabela.addCell(cabecalho);
        }

        if (secao.linhas().isEmpty()) {
            PdfPCell vazio = new PdfPCell(new Phrase("Nenhum registro no período.",
                    new Font(REGULAR, 7.5f, Font.NORMAL, MUTED)));
            vazio.setColspan(secao.colunas().length);
            vazio.setBorderColor(BORDER);
            vazio.setPadding(7f);
            tabela.addCell(vazio);
            return tabela;
        }

        int indice = 0;
        for (Linha linha : secao.linhas()) {
            for (Celula celula : linha.celulas()) {
                PdfPCell item = new PdfPCell(new Phrase(texto(celula.texto()),
                        fonteCelula(celula)));
                item.setBorderColor(BORDER);
                item.setBorderWidth(0.4f);
                item.setPadding(4f);
                item.setBackgroundColor(indice % 2 == 0 ? WHITE : ROW_ALT);
                item.setVerticalAlignment(Element.ALIGN_MIDDLE);
                tabela.addCell(item);
            }
            indice++;
        }
        return tabela;
    }

    private Font fonteCelula(Celula celula) {
        return new Font(celula.negrito() ? BOLD : REGULAR, 7f, Font.NORMAL, cor(celula.tom()));
    }

    private Font valor(Tom tom) {
        return new Font(BOLD, 13f, Font.NORMAL, cor(tom));
    }

    private Color cor(Tom tom) {
        if (tom == null) {
            return INK;
        }
        return switch (tom) {
            case POSITIVO -> SUCCESS;
            case NEGATIVO -> DANGER;
            case ALERTA -> WARNING;
            case MUTED -> MUTED;
            default -> INK;
        };
    }

    private String texto(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor.trim();
    }

    private static byte[] carregarLogo() {
        for (String recurso : new String[] { "/META-INF/resources/images/logo.png",
                "/images/logo.png" }) {
            try (InputStream entrada = RelatorioPdfRenderer.class.getResourceAsStream(recurso)) {
                if (entrada != null) {
                    return entrada.readAllBytes();
                }
            } catch (IOException ex) {
                // tenta o próximo caminho; o fallback é o cabeçalho textual
            }
        }
        return null;
    }

    private static final class Moldura extends PdfPageEventHelper {

        private final int totalPaginas;
        private final String codigo;
        private final String geradoEm;

        private Moldura(int totalPaginas, String codigo, String geradoEm) {
            this.totalPaginas = totalPaginas;
            this.codigo = codigo;
            this.geradoEm = geradoEm;
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            try {
                PdfContentByte conteudo = escritor.getDirectContent();
                Rectangle pagina = documento.getPageSize();
                float topo = pagina.getHeight();
                float xMarca = MARGEM;

                if (LOGO != null) {
                    Image logo = Image.getInstance(LOGO);
                    logo.scaleToFit(30f, 36f);
                    logo.setAbsolutePosition(MARGEM, topo - 58f);
                    conteudo.addImage(logo);
                    xMarca = MARGEM + 38f;
                }

                ColumnText.showTextAligned(conteudo, Element.ALIGN_LEFT,
                        new Phrase("Fundação Manuel Cruz", MARCA), xMarca, topo - 30f, 0);
                ColumnText.showTextAligned(conteudo, Element.ALIGN_LEFT,
                        new Phrase("ManuElo · Gestão financeira e documental", MARCA_SUB),
                        xMarca, topo - 42f, 0);
                ColumnText.showTextAligned(conteudo, Element.ALIGN_RIGHT,
                        new Phrase("RELATÓRIO GERENCIAL", MARCA_SUB),
                        pagina.getWidth() - MARGEM, topo - 30f, 0);

                conteudo.setColorStroke(ACCENT);
                conteudo.setLineWidth(1.2f);
                conteudo.moveTo(MARGEM, topo - 68f);
                conteudo.lineTo(pagina.getWidth() - MARGEM, topo - 68f);
                conteudo.stroke();

                conteudo.setColorStroke(BORDER);
                conteudo.setLineWidth(0.6f);
                conteudo.moveTo(MARGEM, 42f);
                conteudo.lineTo(pagina.getWidth() - MARGEM, 42f);
                conteudo.stroke();

                ColumnText.showTextAligned(conteudo, Element.ALIGN_LEFT,
                        new Phrase("Código " + codigo, RODAPE_CODIGO), MARGEM, 30f, 0);
                ColumnText.showTextAligned(conteudo, Element.ALIGN_CENTER,
                        new Phrase("Página " + escritor.getPageNumber() + " de " + totalPaginas,
                                RODAPE_TEXTO), pagina.getWidth() / 2f, 30f, 0);
                ColumnText.showTextAligned(conteudo, Element.ALIGN_RIGHT,
                        new Phrase(geradoEm, RODAPE_TEXTO),
                        pagina.getWidth() - MARGEM, 30f, 0);
            } catch (Exception ex) {
                throw new IllegalStateException("Falha ao escrever cabeçalho/rodapé do PDF.",
                        ex);
            }
        }
    }
}
