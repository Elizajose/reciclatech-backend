document.addEventListener('DOMContentLoaded', () => {
    const htmlElement = document.documentElement;
    const themeToggleBtn = document.getElementById('themeToggleBtn');
    const themeIcon = document.getElementById('themeIcon');

    // Recupera a preferência salva ou define 'light' como padrão
    const currentTheme = localStorage.getItem('theme') || 'light';

    // Aplica o tema na inicialização
    htmlElement.setAttribute('data-bs-theme', currentTheme);
    updateIcon(currentTheme);

    // Evento de clique para o botão de troca de tema (se existir na tela)
    if (themeToggleBtn) {
        themeToggleBtn.addEventListener('click', (e) => {
            e.stopPropagation(); // Evita que o menu dropdown feche ao trocar o tema

            const isDark = htmlElement.getAttribute('data-bs-theme') === 'dark';
            const newTheme = isDark ? 'light' : 'dark';

            htmlElement.setAttribute('data-bs-theme', newTheme);
            localStorage.setItem('theme', newTheme);
            updateIcon(newTheme);
        });
    }

    // Função que troca apenas o ícone de Sol/Lua
    function updateIcon(theme) {
        if (!themeIcon) return;

        if (theme === 'dark') {
            themeIcon.classList.remove('bi-moon-fill');
            themeIcon.classList.add('bi-sun-fill', 'text-warning');
        } else {
            themeIcon.classList.remove('bi-sun-fill', 'text-warning');
            themeIcon.classList.add('bi-moon-fill');
        }
    }
});