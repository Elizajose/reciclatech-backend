// Ativa o Modo Escuro caso o usuário tenha selecionado em outra tela
document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});

// Centraliza a mensagem de alerta (código mais limpo)
function confirmarExclusao(event) {
    if (!confirm('Tem certeza que deseja excluir este registro de despesa do seu financeiro?')) {
        event.preventDefault(); // Impede o link de ser aberto se a pessoa clicar em "Cancelar"
    }
}