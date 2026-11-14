-- Fase 14 (OPCIONAL: a aplicação já faz isso sozinha ao iniciar).
-- Use só se o log mostrar "Fase 14: não consegui remover a trava antiga de login de funcionário".
-- Faça BACKUP antes. Pode rodar mais de uma vez.

-- 1) conferir a trava antiga (deve aparecer uma linha de tipo 'u' só com a coluna login)
SELECT con.conname, con.contype
FROM pg_constraint con JOIN pg_class rel ON rel.oid = con.conrelid
WHERE rel.relname = 'funcionario' AND con.contype = 'u';

-- 2) remover a trava "login único no sistema todo" (o nome pode variar: troque pelo nome que apareceu acima)
-- ALTER TABLE funcionario DROP CONSTRAINT <nome_da_trava_so_de_login>;

-- 3) conferir os códigos dos armazéns (os NULL ganham código quando o dono abrir "Meus armazéns" ou ao reiniciar)
SELECT id, nome, codigo FROM armazem ORDER BY id;
