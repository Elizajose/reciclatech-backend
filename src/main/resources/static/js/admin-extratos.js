document.addEventListener('DOMContentLoaded', () => {
    // Tema (claro/escuro) escolhido no painel
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);

    const searchInput = document.getElementById('searchInput');
    if (searchInput) searchInput.addEventListener('input', filterExtratos);
});

// Fase 31: filtro por nome (sem acento) ou código do recibo
function filterExtratos() {
    const semAcento = t => (t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    const input = semAcento(document.getElementById('searchInput').value.trim());
    const rows = document.querySelectorAll('.extrato-row');
    let algum = false;
    rows.forEach(row => {
        const mostra = !input || semAcento(row.getAttribute('data-name')).includes(input)
            || semAcento(row.getAttribute('data-id')).replace(/-/g, '').includes(input.replace(/-/g, ''));
        row.classList.toggle('d-none', !mostra);
        if (mostra) algum = true;
    });
    const nada = document.getElementById('semResultado');
    if (nada) nada.classList.toggle('d-none', algum || rows.length === 0);
}
