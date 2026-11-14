/*
 * Botão "Voltar" que volta para a tela de ONDE O USUÁRIO VEIO (e não sempre para o início).
 * Uso: <a href="/destino-padrao" data-voltar>Voltar</a>
 *  - guarda, por página, a última tela do próprio site que levou até aqui (document.referrer);
 *  - se o formulário recarregar a mesma página (erro de validação), a origem guardada continua valendo;
 *  - sem origem conhecida (abriu o link direto), usa o href como padrão;
 *  - data-ignora-auth: não volta para outra tela de entrada (login/cadastro/senha), para não ficar em vai-e-volta.
 */
(function () {
    var AUTH = ['/login', '/conta/cadastro', '/conta/esqueci-senha', '/conta/redefinir-senha'];
    var chave = 'coletae:voltar:' + location.pathname;

    try {
        if (document.referrer) {
            var u = new URL(document.referrer);
            if (u.origin === location.origin && u.pathname !== location.pathname) {
                sessionStorage.setItem(chave, u.pathname + u.search);
            }
        }
    } catch (e) { /* sem sessionStorage: usa o href padrão */ }

    document.addEventListener('click', function (e) {
        var link = e.target.closest ? e.target.closest('[data-voltar]') : null;
        if (!link) return;
        var destino = null;
        try { destino = sessionStorage.getItem(chave); } catch (x) { destino = null; }
        if (!destino || destino.charAt(0) !== '/' || destino.indexOf('//') === 0) return; // só caminhos do próprio site
        var caminho = destino.split('?')[0].replace(/\/+$/, '') || '/';
        if (caminho === location.pathname) return;
        if (link.hasAttribute('data-ignora-auth') && AUTH.indexOf(caminho) >= 0) return;
        e.preventDefault();
        location.href = destino;
    });
})();
