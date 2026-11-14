package com.reciclatech.backend.util;

/** Mesma regra de CPF/CNPJ (inclui CNPJ alfanumérico) usada hoje em TelaController.isCpfCnpjValido. */
public final class DocumentoValidator {

    private DocumentoValidator() {}

    public static boolean valido(String documento) {
        if (documento == null) return false;

        String docLimpo = documento.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();

        if (docLimpo.length() == 11) {
            if (docLimpo.matches("(\\d)\\1{10}")) return false;
            int soma = 0, peso = 10;
            for (int i = 0; i < 9; i++) soma += (docLimpo.charAt(i) - 48) * peso--;
            int digito1 = 11 - (soma % 11);
            if (digito1 > 9) digito1 = 0;
            soma = 0; peso = 11;
            for (int i = 0; i < 10; i++) soma += (docLimpo.charAt(i) - 48) * peso--;
            int digito2 = 11 - (soma % 11);
            if (digito2 > 9) digito2 = 0;
            return (docLimpo.charAt(9) - 48 == digito1) && (docLimpo.charAt(10) - 48 == digito2);

        } else if (docLimpo.length() == 14) {
            if (!docLimpo.substring(12, 14).matches("\\d\\d")) return false;

            int[] valores = new int[14];
            for (int i = 0; i < 14; i++) {
                char c = docLimpo.charAt(i);
                if (Character.isDigit(c)) { valores[i] = c - 48; }
                else if (Character.isLetter(c)) { valores[i] = c - 65 + 17; }
                else { return false; }
            }

            int soma = 0;
            int[] peso1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            for (int i = 0; i < 12; i++) { soma += valores[i] * peso1[i]; }
            int resto = soma % 11;
            int digito1 = resto < 2 ? 0 : 11 - resto;

            soma = 0;
            int[] peso2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            for (int i = 0; i < 12; i++) { soma += valores[i] * peso2[i]; }
            soma += digito1 * 2;
            resto = soma % 11;
            int digito2 = resto < 2 ? 0 : 11 - resto;

            return (valores[12] == digito1) && (valores[13] == digito2);
        }
        return false;
    }

    /** Só letras e números, em maiúsculas. Serve para comparar documentos escritos de formas diferentes. */
    public static String normalizar(String documento) {
        return documento == null ? "" : documento.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }

    /** Formato único para gravar: CPF 000.000.000-00, CNPJ 00.000.000/0000-00; outro tamanho volta aparado. */
    public static String formatar(String documento) {
        String d = normalizar(documento);
        if (d.length() == 11) return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
        if (d.length() == 14) return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/" + d.substring(8, 12) + "-" + d.substring(12);
        return documento == null ? "" : documento.trim();
    }
}
