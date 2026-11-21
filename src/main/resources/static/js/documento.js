// Fase 32: CPF ou CNPJ (inclui o CNPJ alfanumérico que a Receita começa a emitir em 2026).
// Mesma conta do servidor (DocumentoValidator). O servidor continua decidindo; isto só avisa antes de enviar.
(function (global) {
    function limpar(t) { return (t || '').toUpperCase().replace(/[^0-9A-Z]/g, ''); }

    function formatar(texto) {
        var d = limpar(texto);
        var ehCnpj = d.length > 11 || /[A-Z]/.test(d);
        if (!ehCnpj) {
            d = d.slice(0, 11);
            if (d.length > 9) return d.replace(/^(\d{3})(\d{3})(\d{3})(\d{1,2})$/, '$1.$2.$3-$4');
            if (d.length > 6) return d.replace(/^(\d{3})(\d{3})(\d{1,3})$/, '$1.$2.$3');
            if (d.length > 3) return d.replace(/^(\d{3})(\d{1,3})$/, '$1.$2');
            return d;
        }
        // CNPJ: 12 primeiros podem ter letras; os 2 últimos (dígitos verificadores) são números
        d = d.slice(0, 14);
        if (d.length > 12) d = d.slice(0, 12) + d.slice(12).replace(/\D/g, '');
        var p = [d.slice(0, 2), d.slice(2, 5), d.slice(5, 8), d.slice(8, 12), d.slice(12, 14)];
        var r = p[0];
        if (p[1]) r += '.' + p[1];
        if (p[2]) r += '.' + p[2];
        if (p[3]) r += '/' + p[3];
        if (p[4]) r += '-' + p[4];
        return r;
    }

    function cpfValido(d) {
        if (!/^\d{11}$/.test(d) || /^(\d)\1{10}$/.test(d)) return false;
        for (var t = 9; t < 11; t++) {
            var soma = 0;
            for (var i = 0; i < t; i++) soma += parseInt(d.charAt(i), 10) * (t + 1 - i);
            var dig = (soma * 10) % 11;
            if (dig === 10) dig = 0;
            if (dig !== parseInt(d.charAt(t), 10)) return false;
        }
        return true;
    }

    function cnpjValido(d) {
        if (!/^[0-9A-Z]{12}\d{2}$/.test(d)) return false;
        var v = [];
        for (var i = 0; i < 14; i++) v.push(d.charCodeAt(i) - 48);   // '0'..'9' = 0..9, 'A' = 17 ...
        var p1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2], p2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
        var s = 0; for (i = 0; i < 12; i++) s += v[i] * p1[i];
        var r = s % 11, d1 = r < 2 ? 0 : 11 - r;
        s = 0; for (i = 0; i < 12; i++) s += v[i] * p2[i]; s += d1 * 2;
        r = s % 11; var d2 = r < 2 ? 0 : 11 - r;
        return v[12] === d1 && v[13] === d2;
    }

    function valido(texto) {
        var d = limpar(texto);
        return d.length === 11 ? cpfValido(d) : d.length === 14 ? cnpjValido(d) : false;
    }

    function ligar(input) {
        if (!input) return;
        input.addEventListener('input', function () { input.value = formatar(input.value); });
        if (input.value) input.value = formatar(input.value);
    }

    global.Documento = { formatar: formatar, valido: valido, ligar: ligar, limpar: limpar };
})(window);
