document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});

// Lógica para recuperar rascunho
document.addEventListener("DOMContentLoaded", function() {
    let idVendedorElement = document.getElementById('idVendedor');
    if (!idVendedorElement) return; // Evita erro se o campo não carregar

    let idVendedor = idVendedorElement.value;
    let rascunhoString = sessionStorage.getItem('rascunho_pesagem_' + idVendedor);

    if (rascunhoString) {
        let rascunho = JSON.parse(rascunhoString);
        let agora = new Date().getTime();

        if (agora - rascunho.timestamp < 7200000) {
            for (let idInput in rascunho.pesos) {
                let campoTotal = document.getElementById(idInput);
                if (campoTotal) { campoTotal.value = rascunho.pesos[idInput]; }
            }
        } else { sessionStorage.removeItem('rascunho_pesagem_' + idVendedor); }
    }
});

function adicionarAoTotal(id) {
    let inputParcial = document.getElementById('parcial_' + id);
    let inputTotal = document.getElementById('total_' + id);

    let valorParcial = parseFloat(inputParcial.value.replace(',', '.'));
    let valorTotal = parseFloat(inputTotal.value) || 0;

    if (!isNaN(valorParcial) && valorParcial > 0) {
        let novoTotal = valorTotal + valorParcial;
        inputTotal.value = novoTotal.toFixed(2);
        inputParcial.value = '';
        inputParcial.focus();
    }
}

function zerarTotal(id) {
    document.getElementById('total_' + id).value = '';
    document.getElementById('parcial_' + id).value = '';
}

function validarAntesDeEnviar() {
    let idVendedor = document.getElementById('idVendedor').value;
    let inputsParciais = document.querySelectorAll('input[id^="parcial_"]');

    inputsParciais.forEach(function(input) {
        let valor = parseFloat(input.value.replace(',', '.'));
        if (!isNaN(valor) && valor > 0) {
            let idMaterial = input.id.split('_')[1];
            adicionarAoTotal(idMaterial);
        }
    });

    let rascunho = { timestamp: new Date().getTime(), pesos: {} };
    let inputsTotais = document.querySelectorAll('input[id^="total_"]');
    inputsTotais.forEach(function(input) {
        if (input.value && parseFloat(input.value) > 0) { rascunho.pesos[input.id] = input.value; }
    });

    sessionStorage.setItem('rascunho_pesagem_' + idVendedor, JSON.stringify(rascunho));
    return true;
}

function limparRascunho() {
    let idVendedor = document.getElementById('idVendedor').value;
    sessionStorage.removeItem('rascunho_pesagem_' + idVendedor);
}