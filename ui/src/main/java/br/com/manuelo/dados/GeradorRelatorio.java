package br.com.manuelo.dados;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Component
public class GeradorRelatorio {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy",
            Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public String gerar(String formato, String titulo, LocalDate inicio, LocalDate fim,
            MovimentacaoRepositorio.Resumo resumo, List<List<String>> linhas) {
        try {
            Path diretorio = Path.of(System.getProperty("java.io.tmpdir"), "manuelo-relatorios");
            Files.createDirectories(diretorio);
            boolean xlsx = "XLSX".equalsIgnoreCase(formato);
            String extensao = xlsx ? "xlsx" : "pdf";
            Path destino = diretorio.resolve("relatorio-" + ARQUIVO.format(java.time.LocalDateTime.now())
                    + "-" + System.nanoTime() + "." + extensao);
            if (xlsx) {
                gerarXlsx(destino, titulo, inicio, fim, resumo, linhas);
            } else {
                gerarPdf(destino, titulo, inicio, fim, resumo, linhas);
            }
            return destino.toAbsolutePath().toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao gerar o arquivo do relatório: " + ex.getMessage(), ex);
        }
    }

    private void gerarPdf(Path destino, String titulo, LocalDate inicio, LocalDate fim,
            MovimentacaoRepositorio.Resumo resumo, List<List<String>> linhas) throws Exception {
        Document documento = new Document();
        PdfWriter.getInstance(documento, new FileOutputStream(destino.toFile()));
        documento.open();

        Paragraph cabecalho = new Paragraph(titulo);
        cabecalho.setAlignment(Element.ALIGN_CENTER);
        documento.add(cabecalho);
        documento.add(new Paragraph(periodo(inicio, fim)));

        if (resumo != null) {
            documento.add(new Paragraph("Entradas: " + dinheiro(resumo.entradas())
                    + "    Saídas: " + dinheiro(resumo.saidas())
                    + "    Saldo: " + dinheiro(resumo.saldo())));
        }
        documento.add(new Paragraph(" "));

        PdfPTable tabela = new PdfPTable(4);
        tabela.setWidthPercentage(100);
        for (String coluna : List.of("Data", "Descrição", "Categoria", "Valor")) {
            PdfPCell cell = new PdfPCell(new Paragraph(coluna));
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            tabela.addCell(cell);
        }
        for (List<String> linha : linhas) {
            for (String valor : linha) {
                tabela.addCell(new Paragraph(valor == null ? "" : valor));
            }
        }
        documento.add(tabela);
        documento.close();
    }

    private void gerarXlsx(Path destino, String titulo, LocalDate inicio, LocalDate fim,
            MovimentacaoRepositorio.Resumo resumo, List<List<String>> linhas) throws Exception {
        try (XSSFWorkbook livro = new XSSFWorkbook()) {
            Sheet planilha = livro.createSheet("Relatório");
            int indice = 0;
            planilha.createRow(indice++).createCell(0).setCellValue(titulo);
            planilha.createRow(indice++).createCell(0).setCellValue(periodo(inicio, fim));
            if (resumo != null) {
                planilha.createRow(indice++).createCell(0)
                        .setCellValue("Entradas: " + dinheiro(resumo.entradas())
                                + "  Saídas: " + dinheiro(resumo.saidas())
                                + "  Saldo: " + dinheiro(resumo.saldo()));
            }
            indice++;
            Row cabecalho = planilha.createRow(indice++);
            String[] colunas = { "Data", "Descrição", "Categoria", "Valor" };
            for (int c = 0; c < colunas.length; c++) {
                cabecalho.createCell(c).setCellValue(colunas[c]);
            }
            for (List<String> linha : linhas) {
                Row row = planilha.createRow(indice++);
                for (int c = 0; c < linha.size(); c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(linha.get(c) == null ? "" : linha.get(c));
                }
            }
            try (FileOutputStream saida = new FileOutputStream(destino.toFile())) {
                livro.write(saida);
            }
        }
    }

    private String periodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            return "Data de referência: " + DATA.format(LocalDate.now());
        }
        return "Período: " + DATA.format(inicio) + " a " + DATA.format(fim);
    }

    private String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "R$ 0,00";
        }
        return "R$ " + valor.toPlainString().replace(".", ",");
    }
}
