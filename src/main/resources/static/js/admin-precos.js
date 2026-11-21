// Fase 32: Tabela de Preços (lista em cartões). Tema, ordem alfabética, busca e rótulo do preço.
document.addEventListener('DOMContentLoaded', () => {
    try { document.documentElement.setAttribute('data-bs-theme', localStorage.getItem('theme') || 'light'); } catch (e) { /* sem storage */ }

    const lista = document.getElementById('tabelaMateriais');
    const vazio = document.getElementById('msgVazia');
    if (lista) {
        const linhas = Array.from(lista.querySelectorAll('.linha-material'));
        const nome = (el) => { const n = el.querySelector('.nome-item'); return n ? n.textContent.trim().toLowerCase() : ''; };
        linhas.sort((a, b) => nome(a).localeCompare(nome(b), 'pt-BR'));
        linhas.forEach(l => lista.insertBefore(l, vazio));

        const busca = document.getElementById('campoBusca');
        const sem = (t) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
        if (busca) busca.addEventListener('input', () => {
            const q = sem(busca.value.trim());
            let algum = false;
            linhas.forEach(l => { const ok = !q || sem(nome(l)).includes(q); l.classList.toggle('d-none', !ok); if (ok) algum = true; });
            if (vazio) vazio.style.display = algum ? 'none' : '';
        });
    }

    // o rótulo do preço acompanha a unidade escolhida
    const rotulo = document.getElementById('rotuloPreco');
    document.querySelectorAll('input[name=unidade]').forEach(r => r.addEventListener('change', () => {
        if (rotulo) rotulo.textContent = r.value === 'UN' && r.checked ? 'Preço por unidade' : 'Preço por quilo';
    }));

    const form = document.getElementById('formImportar');
    if (form) form.addEventListener('submit', () => {
        const b = form.querySelector('button[type=submit]');
        if (b) { b.disabled = true; b.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Lendo a planilha...'; }
    });
});
