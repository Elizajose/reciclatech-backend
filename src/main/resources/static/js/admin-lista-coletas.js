document.addEventListener('DOMContentLoaded', () => {

    // ==========================================
    // 1. LÓGICA DO TEMA (LIGHT/DARK)
    // ==========================================
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

    // ==========================================
    // 2. MÁSCARA DE CPF PARA O BALCÃO
    // ==========================================
    const cpfInput = document.getElementById('cpfInput');
    if (cpfInput) {
        cpfInput.addEventListener('input', function() {
            let v = this.value.replace(/\D/g, ""); // Remove tudo que não é número
            if (v.length > 11) v = v.substring(0, 11); // Trava em exatos 11 números

            // Aplica a formatação 000.000.000-00 visualmente
            if (v.length > 9) {
                v = v.replace(/(\d{3})(\d{3})(\d{3})(\d{1,2})/, "$1.$2.$3-$4");
            } else if (v.length > 6) {
                v = v.replace(/(\d{3})(\d{3})(\d{1,3})/, "$1.$2.$3");
            } else if (v.length > 3) {
                v = v.replace(/(\d{3})(\d{1,3})/, "$1.$2");
            }

            this.value = v;
        });
    }
});