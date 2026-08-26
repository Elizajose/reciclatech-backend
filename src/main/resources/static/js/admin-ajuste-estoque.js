document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});

// FUNÇÃO: Atualiza a etiqueta da unidade com base no material selecionado
function atualizarUnidade() {
    const select = document.getElementById('selectMaterial');
    const selectedOption = select.options[select.selectedIndex];

    // Pega a unidade do banco (ex: UN, KG) ou deixa KG por padrão se der erro
    const unidade = selectedOption.getAttribute('data-unidade') || 'KG';

    document.getElementById('unidadeLabel').innerText = unidade;
}