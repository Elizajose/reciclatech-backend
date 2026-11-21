// Fase 32: conferência editável. Filtro, destaque do que foi editado, trava contra duplo clique.
(function () {
    try { var tema = localStorage.getItem('theme'); if (tema) document.documentElement.setAttribute('data-bs-theme', tema); } catch (e) { /* sem storage */ }

    document.querySelectorAll('input[name=filtro]').forEach(function (r) {
        r.addEventListener('change', function () {
            var f = document.querySelector('input[name=filtro]:checked').value;
            document.querySelectorAll('tbody tr[data-tipo]').forEach(function (tr) {
                var t = tr.getAttribute('data-tipo');
                var mostra = !f || (f === 'muda' ? (t === 'NOVO' || t === 'ALTERAR') : t === f);
                tr.classList.toggle('d-none', !mostra);
            });
        });
    });

    // marca o que foi mexido; com algo mexido, "Confirmar" vira "revise antes"
    var editado = false;
    var aviso = document.getElementById('avisoEditado');
    var confirmar = document.getElementById('btnConfirmar');
    function marcar(el) {
        var original = el.type === 'checkbox' ? el.defaultChecked : (el.tagName === 'SELECT' ? (el.querySelector('option[selected]') || {}).value || '' : el.defaultValue);
        var atual = el.type === 'checkbox' ? el.checked : el.value;
        el.classList.toggle('editado', String(original) !== String(atual));
        editado = document.querySelectorAll('.editado').length > 0;
        if (aviso) aviso.style.display = editado ? '' : 'none';
        if (el.tagName === 'SELECT') el.classList.toggle('sem-unidade', el.value === '');
    }
    document.querySelectorAll('.campo, .incluir').forEach(function (el) {
        el.addEventListener('input', function () { marcar(el); });
        el.addEventListener('change', function () { marcar(el); });
    });

    var form = document.getElementById('formConferencia');
    var clicado = null;
    document.querySelectorAll('button[name=acao]').forEach(function (b) { b.addEventListener('click', function () { clicado = b; }); });
    if (form) form.addEventListener('submit', function (ev) {
        var acao = clicado ? clicado.value : 'revisar';
        if (acao === 'confirmar' && !editado) {
            var c = document.getElementById('confirmarVariacoes');
            if (c && !c.checked) { ev.preventDefault(); alert('Marque "Conferi os preços em amarelo" antes de confirmar.'); c.focus(); return; }
        }
        // o botão clicado vai junto no envio; só depois trava os dois
        setTimeout(function () {
            document.querySelectorAll('button[name=acao]').forEach(function (b) { b.disabled = true; });
            if (clicado) clicado.innerHTML = '<span class="spinner-border spinner-border-sm"></span> ' + (acao === 'confirmar' ? 'Gravando...' : 'Conferindo...');
        }, 0);
    });
    // Enter num campo = Revisar (nunca grava por engano)
    if (form) form.addEventListener('keydown', function (ev) {
        if (ev.key === 'Enter' && ev.target.tagName === 'INPUT') { ev.preventDefault(); var r = document.getElementById('btnRevisar'); if (r) r.click(); }
    });
})();
