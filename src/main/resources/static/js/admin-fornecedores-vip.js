document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});

// Centraliza o alerta para evitar poluição no código HTML
function confirmarRemocaoVip(event) {
    if (!confirm('Deseja remover este preço especial?')) {
        event.preventDefault(); // Impede o link de excluir caso clique em "Cancelar"
    }
}