// Ativa o Modo Escuro caso o usuário tenha selecionado na tela de perfil
document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});