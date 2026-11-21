package com.reciclatech.backend.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Fase 30: o Hibernate cria no PostgreSQL uma trava (CHECK) com os valores que um campo "enum" aceitava
 * quando a tabela foi criada, e o ddl-auto=update NUNCA atualiza essa trava. Valor novo no enum
 * (ex.: CANCELADO, criado na Fase 8) passa a ser recusado pelo banco ("violates check constraint").
 *
 * Este SQL, idempotente, só age quando a trava existente NÃO aceita algum dos valores atuais:
 * remove a(s) trava(s) daquela coluna e cria uma nova com todos os valores atuais.
 * Sem trava nenhuma, não faz nada. Nomes de tabela/coluna/valores vêm do próprio código (nunca do usuário).
 */
public final class RestricaoEnum {

    private static final Pattern IDENT = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    private static final Pattern VALOR = Pattern.compile("[A-Z0-9_]{1,60}");

    private RestricaoEnum() {}

    public static String sqlAtualizar(String tabela, String coluna, List<String> valores) {
        if (!IDENT.matcher(tabela).matches() || !IDENT.matcher(coluna).matches()) {
            throw new IllegalArgumentException("Nome de tabela/coluna inválido");
        }
        if (valores == null || valores.isEmpty()) throw new IllegalArgumentException("Sem valores");
        StringBuilder faltando = new StringBuilder();
        StringBuilder lista = new StringBuilder();
        for (String v : valores) {
            if (!VALOR.matcher(v).matches()) throw new IllegalArgumentException("Valor inválido: " + v);
            if (faltando.length() > 0) { faltando.append(" OR "); lista.append(", "); }
            faltando.append("pg_get_constraintdef(con.oid) NOT LIKE '%''").append(v).append("''%'");
            lista.append("''").append(v).append("''");
        }
        String nomeNovo = tabela + "_" + coluna + "_check";
        return "DO $$ DECLARE r record; achou boolean := false; BEGIN "
                + "FOR r IN SELECT con.conname FROM pg_constraint con "
                + "  JOIN pg_class rel ON rel.oid = con.conrelid "
                + "  JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                + "  WHERE rel.relname = '" + tabela + "' AND ns.nspname = current_schema() AND con.contype = 'c' "
                + "  AND array_length(con.conkey, 1) = 1 "
                + "  AND (SELECT a.attname FROM pg_attribute a WHERE a.attrelid = rel.oid AND a.attnum = con.conkey[1]) = '" + coluna + "' "
                + "  AND (" + faltando + ") "
                + "LOOP EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', '" + tabela + "', r.conname); achou := true; END LOOP; "
                + "IF achou THEN EXECUTE 'ALTER TABLE " + tabela + " ADD CONSTRAINT " + nomeNovo
                + " CHECK (" + coluna + " IS NULL OR " + coluna + " IN (" + lista + "))'; END IF; "
                + "END $$;";
    }
}
