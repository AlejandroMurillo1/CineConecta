package com.cineconecta.platform.reporting.internal.csv;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Genera CSV según RFC 4180 (separador coma, fin de línea CRLF, comillas dobles cuando hace falta).
 *
 * <ul>
 *   <li>Se antepone el BOM de UTF-8 para que Excel muestre bien las tildes.</li>
 *   <li>Los números se escriben sin separador de miles y con punto decimal, para que sigan
 *       siendo datos y no texto al abrirlos en otra herramienta.</li>
 *   <li>Un texto que empieza con {@code = + - @}, tabulación o retorno se antepone con un apóstrofo
 *       para que una hoja de cálculo no lo ejecute como fórmula (inyección CSV). Aplica, por ejemplo,
 *       al nombre de un plan creado por un administrador.</li>
 * </ul>
 */
public final class CsvWriter {

    private static final String SEPARATOR = ",";
    private static final String LINE_END = "\r\n";
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvWriter() {
    }

    /** CSV completo como bytes UTF-8 con BOM, listo para descargar. */
    public static byte[] toUtf8WithBom(List<String> header, List<? extends List<?>> rows) {
        byte[] body = toText(header, rows).getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
        return out;
    }

    /** CSV completo como texto (sin BOM). */
    public static String toText(List<String> header, List<? extends List<?>> rows) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, header);
        for (List<?> row : rows) {
            appendLine(sb, row);
        }
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(cell(values.get(i)));
        }
        sb.append(LINE_END);
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        String text = guardFormula(value.toString());
        boolean needsQuotes = text.contains(SEPARATOR) || text.contains("\"")
                || text.contains("\n") || text.contains("\r")
                || (!text.isEmpty() && (Character.isWhitespace(text.charAt(0))
                        || Character.isWhitespace(text.charAt(text.length() - 1))));
        return needsQuotes ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private static String guardFormula(String text) {
        if (text.isEmpty()) {
            return text;
        }
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + text;
        }
        return text;
    }
}
