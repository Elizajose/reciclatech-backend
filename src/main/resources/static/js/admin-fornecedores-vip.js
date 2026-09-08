document.addEventListener('DOMContentLoaded', () => {
    // 1. Lógica do Tema (Dark/Light)
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);

    // 2. Máscara de CPF para o Fornecedor VIP
    const cpfVipInput = document.getElementById('cpfVipInput');
    if (cpfVipInput) {
        cpfVipInput.addEventListener('input', function() {
            let v = this.value.replace(/\D/g, ""); // Remove não-números
            if (v.length > 11) v = v.substring(0, 11);

            // Aplica a máscara visualmente
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

// 3. Centraliza o alerta para evitar poluição no código HTML
function confirmarRemocaoVip(event) {
    if (!confirm('Deseja remover este preço especial?')) {
        event.preventDefault(); // Impede o link de excluir caso clique em "Cancelar"
    }
}