// Fase 32: Preços VIP. CPF ou CNPJ, preço da tabela ao lado, "+ Material" no cartão do fornecedor, busca.
document.addEventListener('DOMContentLoaded', () => {
    try { document.documentElement.setAttribute('data-bs-theme', localStorage.getItem('theme') || 'light'); } catch (e) { /* sem storage */ }

    const doc = document.getElementById('cpfVipInput');
    const erro = document.getElementById('cpfVipErro');
    if (window.Documento) Documento.ligar(doc);

    const form = document.getElementById('formVip');
    if (form) form.addEventListener('submit', (ev) => {
        if (window.Documento && doc && !Documento.valido(doc.value)) {
            ev.preventDefault();
            if (erro) erro.classList.remove('d-none');
            doc.focus();
            return;
        }
        const b = form.querySelector('button[type=submit]');
        if (b) b.disabled = true;
    });
    if (doc) doc.addEventListener('input', () => { if (erro) erro.classList.add('d-none'); });

    // mostra o preço da tabela do material escolhido (para comparar com o VIP)
    const sel = document.getElementById('materialVip');
    const info = document.getElementById('precoTabelaVip');
    if (sel && info) sel.addEventListener('change', () => {
        const op = sel.options[sel.selectedIndex];
        const p = op ? parseFloat(op.getAttribute('data-preco')) : NaN;
        info.textContent = isNaN(p) ? '' : 'Tabela hoje: R$ ' + p.toFixed(2).replace('.', ',') + (op.getAttribute('data-un') === 'UN' ? ' por unidade' : ' por kg');
    });

    // "+ Material" preenche o documento e leva ao formulário
    const cartao = document.getElementById('cartaoNovoVip');
    document.querySelectorAll('.mais-material').forEach(b => b.addEventListener('click', () => {
        if (doc) { doc.value = window.Documento ? Documento.formatar(b.getAttribute('data-cpf')) : b.getAttribute('data-cpf'); }
        if (cartao) {
            cartao.scrollIntoView({ behavior: 'smooth', block: 'start' });
            cartao.classList.add('destaque');
            setTimeout(() => cartao.classList.remove('destaque'), 1500);
        }
        if (sel) sel.focus();
    }));

    const busca = document.getElementById('buscaVip');
    const vazio = document.getElementById('vipVazio');
    const sem = (t) => (t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    if (busca) busca.addEventListener('input', () => {
        const q = sem(busca.value.trim());
        const qd = q.replace(/[^0-9a-z]/g, '');
        let algum = false;
        document.querySelectorAll('.grupo-vip').forEach(g => {
            const t = sem(g.getAttribute('data-busca'));
            const ok = !q || t.includes(q) || (qd && t.replace(/[^0-9a-z ]/g, '').includes(qd));
            g.classList.toggle('d-none', !ok);
            if (ok) algum = true;
        });
        if (vazio) vazio.style.display = algum ? 'none' : '';
    });
});
