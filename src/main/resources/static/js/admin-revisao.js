document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});

// Formata o CPF dinamicamente enquanto o usuário digita
function formatarCPF(i) {
    var v = i.value.replace(/\D/g, "");
    if (v.length > 11) v = v.slice(0, 11);

    if (v.length > 9) {
        v = v.replace(/(\d{3})(\d{3})(\d{3})(\d+)/, "$1.$2.$3-$4");
    } else if (v.length > 6) {
        v = v.replace(/(\d{3})(\d{3})(\d+)/, "$1.$2.$3");
    } else if (v.length > 3) {
        v = v.replace(/(\d{3})(\d+)/, "$1.$2");
    }

    i.value = v;
}