document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});

function calcularTotais() {
    let totalGeral = 0;
    const linhas = document.querySelectorAll('tbody tr');

    linhas.forEach(linha => {
        const peso = parseFloat(linha.querySelector('.item-peso').value) || 0;
        const preco = parseFloat(linha.querySelector('.item-preco').value) || 0;
        const subtotal = peso * preco;

        linha.querySelector('.subtotal-text').innerText = 'R$ ' + subtotal.toLocaleString('pt-BR', {minimumFractionDigits: 2, maximumFractionDigits: 2});
        totalGeral += subtotal;
    });

    document.getElementById('totalReceber').innerText = 'R$ ' + totalGeral.toLocaleString('pt-BR', {minimumFractionDigits: 2, maximumFractionDigits: 2});
}

function aplicarMascaraCnpjCpf(input) {
    let valor = input.value.replace(/\D/g, ''); // Remove tudo que não for número

    // Trava para não aceitar mais de 14 números
    if (valor.length > 14) {
        valor = valor.substring(0, 14);
    }

    // Aplica a máscara dependendo do tamanho (CPF ou CNPJ)
    if (valor.length <= 11) {
        valor = valor.replace(/(\d{3})(\d)/, '$1.$2');
        valor = valor.replace(/(\d{3})(\d)/, '$1.$2');
        valor = valor.replace(/(\d{3})(\d{1,2})$/, '$1-$2');
    } else {
        valor = valor.replace(/^(\d{2})(\d)/, '$1.$2');
        valor = valor.replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3');
        valor = valor.replace(/\.(\d{3})(\d)/, '.$1/$2');
        valor = valor.replace(/(\d{4})(\d)/, '$1-$2');
    }

    input.value = valor;
}