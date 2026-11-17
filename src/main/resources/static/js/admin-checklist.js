document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});

// Rascunho automático (Fase 22): cada peso digitado/somado é guardado no navegador e volta se a tela for fechada ou recarregada.
document.addEventListener("DOMContentLoaded", function() {
    var idVendedorElement = document.getElementById('idVendedor');
    if (!idVendedorElement || !window.Rascunho) return;
    var armazem = (document.getElementById('idArmazem') || {}).value || '0';
    var k = Rascunho.chave(armazem, idVendedorElement.value, 'pesagem');

    function salvar() {
        var campos = {};
        document.querySelectorAll('input[id^="total_"], input[id^="parcial_"]').forEach(function(i) {
            if (i.value !== '') campos[i.id] = i.value;
        });
        if (Object.keys(campos).length === 0) Rascunho.apagar(k); else Rascunho.gravar(k, campos);
    }
    window.salvarRascunhoPesagem = salvar;
    document.addEventListener('input', salvar);

    var r = Rascunho.ler(k);
    if (r) {
        var restaurou = false;
        Object.keys(r.campos).forEach(function(idInput) {
            var campo = document.getElementById(idInput);
            if (campo && campo.value === '') { campo.value = r.campos[idInput]; restaurou = true; }
        });
        if (restaurou) {
            Rascunho.avisar(idVendedorElement.form || document.body, r.t, function() {
                document.querySelectorAll('input[id^="total_"], input[id^="parcial_"]').forEach(function(i) { i.value = ''; });
                Rascunho.apagar(k);
            });
        }
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
        if (window.salvarRascunhoPesagem) salvarRascunhoPesagem();
    }
}

function zerarTotal(id) {
    document.getElementById('total_' + id).value = '';
    document.getElementById('parcial_' + id).value = '';
    if (window.salvarRascunhoPesagem) salvarRascunhoPesagem();
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

    if (window.salvarRascunhoPesagem) salvarRascunhoPesagem();   // o rascunho fica guardado até o recibo ser gerado
    return true;
}

function limparRascunho() {
    var v = document.getElementById('idVendedor');
    if (v && window.Rascunho) Rascunho.apagarDoCliente(v.value);
}
