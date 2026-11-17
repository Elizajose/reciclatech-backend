/*
 * Rascunho automático da pesagem (Fase 22).
 * Guarda no PRÓPRIO navegador (localStorage) o que foi digitado, a cada alteração. Se a sessão cair, o navegador fechar
 * ou a página recarregar, o operador volta para a tela e encontra os pesos e preços de volta, com um aviso para conferir.
 * O rascunho é apagado: ao cancelar o atendimento, ao finalizar com sucesso (recibo aberto) ou ao passar de 4 horas.
 * Nada disto vai para o servidor; quem decide e grava continua sendo o servidor, no envio final.
 */
(function () {
    var PREFIXO = 'coletae:rascunho:v1:';
    var VALIDADE_MS = 4 * 60 * 60 * 1000;

    function chave(armazem, vendedor, tipo) { return PREFIXO + armazem + ':' + vendedor + ':' + tipo; }

    function ler(k) {
        try {
            var s = window.localStorage.getItem(k);
            if (!s) return null;
            var d = JSON.parse(s);
            if (!d || typeof d.t !== 'number' || !d.campos || Date.now() - d.t > VALIDADE_MS) {
                window.localStorage.removeItem(k);
                return null;
            }
            return d;
        } catch (e) { return null; }
    }

    function gravar(k, campos) {
        try { window.localStorage.setItem(k, JSON.stringify({ t: Date.now(), campos: campos })); return true; }
        catch (e) { return false; }
    }

    function apagar(k) { try { window.localStorage.removeItem(k); } catch (e) { /* navegador sem armazenamento */ } }

    /** Apaga tudo do cliente (pesagem e revisão), em qualquer armazém. */
    function apagarDoCliente(vendedor) {
        try {
            var remover = [];
            for (var i = 0; i < window.localStorage.length; i++) {
                var k = window.localStorage.key(i);
                if (k && k.indexOf(PREFIXO) === 0 &&
                    (k.slice(-(vendedor.length + 9)) === ':' + vendedor + ':pesagem' ||
                     k.slice(-(vendedor.length + 9)) === ':' + vendedor + ':revisao')) remover.push(k);
            }
            remover.forEach(function (k) { window.localStorage.removeItem(k); });
        } catch (e) { /* ignora */ }
    }

    /** Rascunhos vencidos somem sozinhos, para não acumular lixo no navegador. */
    function limparVencidos() {
        try {
            var todos = [];
            for (var i = 0; i < window.localStorage.length; i++) {
                var k = window.localStorage.key(i);
                if (k && k.indexOf(PREFIXO) === 0) todos.push(k);
            }
            todos.forEach(ler);
        } catch (e) { /* ignora */ }
    }

    function hora(t) {
        try { return new Date(t).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }); }
        catch (e) { return ''; }
    }

    /** Faixa de aviso no topo da página, com botão para descartar. */
    function avisar(onde, t, aoDescartar) {
        var d = document.createElement('div');
        d.className = 'alert alert-info d-flex flex-wrap align-items-center justify-content-between gap-2 py-2 px-3 m-2 small fw-bold';
        d.setAttribute('role', 'status');
        var txt = document.createElement('span');
        txt.textContent = 'Rascunho recuperado (salvo às ' + hora(t) + '). Confira os valores antes de continuar.';
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'btn btn-sm btn-outline-secondary fw-bold';
        b.textContent = 'Descartar rascunho';
        b.onclick = function () { aoDescartar(); d.remove(); };
        d.appendChild(txt);
        d.appendChild(b);
        onde.insertBefore(d, onde.firstChild);
    }

    window.Rascunho = { chave: chave, ler: ler, gravar: gravar, apagar: apagar, apagarDoCliente: apagarDoCliente, avisar: avisar, limparVencidos: limparVencidos };
    document.addEventListener('DOMContentLoaded', limparVencidos);
})();
