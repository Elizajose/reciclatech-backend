document.addEventListener('DOMContentLoaded', () => {
    // 1. Aplica o Tema Escuro
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);

    // 2. Conecta a barra de busca à função de filtro
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('keyup', filterExtratos);
    }
});

// Filtro de pesquisa em tempo real
function filterExtratos() {
    const input = document.getElementById('searchInput').value.toLowerCase();
    const rows = document.querySelectorAll('.extrato-row');

    rows.forEach(row => {
        const name = row.getAttribute('data-name').toLowerCase();
        const id = row.getAttribute('data-id').toLowerCase();

        if (name.includes(input) || id.includes(input)) {
            row.style.display = '';
        } else {
            row.style.display = 'none';
        }
    });
}