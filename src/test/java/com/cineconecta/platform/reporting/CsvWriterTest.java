package com.cineconecta.platform.reporting;

import com.cineconecta.platform.reporting.internal.csv.CsvWriter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {

    @Test
    void writesHeaderRowsAndCrlfLineEndings() {
        String csv = CsvWriter.toText(
                List.of("Plan", "Suscripciones", "Total"),
                List.of(List.of("Básico", 3L, new BigDecimal("50700.00"))));

        assertEquals("Plan,Suscripciones,Total\r\nBásico,3,50700.00\r\n", csv);
    }

    @Test
    void quotesValuesWithSeparatorsQuotesOrLineBreaks() {
        String csv = CsvWriter.toText(
                List.of("a", "b", "c"),
                List.of(List.of("Plan, familiar", "Plan \"Pro\"", "dos\nlíneas")));

        assertEquals("a,b,c\r\n\"Plan, familiar\",\"Plan \"\"Pro\"\"\",\"dos\nlíneas\"\r\n", csv);
    }

    @Test
    void neutralizesSpreadsheetFormulasInText() {
        String csv = CsvWriter.toText(List.of("Plan"), List.of(List.of("=HYPERLINK(\"x\")")));

        assertEquals("Plan\r\n\"'=HYPERLINK(\"\"x\"\")\"\r\n", csv);
    }

    @Test
    void writesNumbersAsPlainDataAndNullAsEmpty() {
        String csv = CsvWriter.toText(List.of("n", "m"),
                List.of(Arrays.asList(new BigDecimal("1E+3"), null)));

        assertEquals("n,m\r\n1000,\r\n", csv);
    }

    @Test
    void prependsUtf8BomForSpreadsheets() {
        byte[] bytes = CsvWriter.toUtf8WithBom(List.of("Plan"), List.of());

        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                Arrays.copyOf(bytes, 3));
        assertEquals("Plan\r\n", new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8));
    }
}
