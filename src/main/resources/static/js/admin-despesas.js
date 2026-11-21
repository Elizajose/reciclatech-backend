// Fase 32: Despesas. Tema, sugestões de descrição e trava contra duplo clique.
document.addEventListener('DOMContentLoaded', () => {
    try { document.documentElement.setAttribute('data-bs-theme', localStorage.getItem('theme') || 'light'); } catch (e) { /* sem storage */ }
    const desc = document.getElementById('descDespesa');
    document.querySelectorAll('.sugestao').forEach(b => b.addEventListener('click', () => {
        if (!desc) return;
        desc.value = b.textContent.trim();
        const v = document.getElementById('valorDespesa');
        if (v) v.focus();
    }));
    const form = document.getElementById('formDespesa');
    if (form) form.addEventListener('submit', () => { const b = form.querySelector('button[type=submit]'); if (b) b.disabled = true; });
});
