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

// Mesma conferência de CPF que o servidor faz (o servidor continua sendo quem decide; isto só evita perder a pesagem por um erro de digitação)
function cpfOuVazioValido(texto) {
    var d = (texto || '').replace(/\D/g, '');
    if (d.length === 0) return true;
    if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
    for (var t = 9; t < 11; t++) {
        var soma = 0;
        for (var i = 0; i < t; i++) soma += parseInt(d.charAt(i), 10) * (t + 1 - i);
        var dig = (soma * 10) % 11;
        if (dig === 10) dig = 0;
        if (dig !== parseInt(d.charAt(t), 10)) return false;
    }
    return true;
}

// Antes de enviar: confere o CPF e trava o botão para não gravar duas vezes
function prepararEnvio(form) {
    var b = form.querySelector('button[type=submit]');
    if (b.disabled) return false;
    var cpf = form.querySelector('input[name=cpfFinal]');
    if (cpf && !cpfOuVazioValido(cpf.value)) {
        alert('CPF inválido. Confira os números ou deixe o campo em branco.');
        cpf.focus();
        return false;
    }
    b.disabled = true;
    b.innerText = 'Gravando...';
    return true;
}


// Rascunho automático da revisão (Fase 22): preços e CPF digitados voltam se a tela for fechada/recarregada.
document.addEventListener('DOMContentLoaded', function () {
    var vend = document.querySelector('input[name=idVendedor]');
    if (!vend || !window.Rascunho) return;
    var armazem = (document.getElementById('idArmazem') || {}).value || '0';
    var k = Rascunho.chave(armazem, vend.value, 'revisao');
    var ids = document.querySelectorAll('input[name=idsMateriais]');
    var precos = document.querySelectorAll('input[name=precosFinais]');
    var cpf = document.querySelector('input[name=cpfFinal]');
    if (ids.length !== precos.length) return;

    function salvar() {
        var campos = {};
        for (var i = 0; i < ids.length; i++) if (precos[i].value !== '') campos['preco_' + ids[i].value] = precos[i].value;
        if (cpf && cpf.value !== '') campos.cpf = cpf.value;
        Rascunho.gravar(k, campos);
    }
    document.addEventListener('input', salvar);

    var r = Rascunho.ler(k);
    if (!r) return;
    var original = {};
    for (var i = 0; i < ids.length; i++) original['preco_' + ids[i].value] = precos[i].value;
    var mudou = false;
    for (var j = 0; j < ids.length; j++) {
        var guardado = r.campos['preco_' + ids[j].value];
        if (guardado !== undefined && guardado !== precos[j].value) { precos[j].value = guardado; mudou = true; }
    }
    if (cpf && r.campos.cpf !== undefined && r.campos.cpf !== cpf.value) { cpf.value = r.campos.cpf; mudou = true; }
    if (mudou) {
        Rascunho.avisar(document.querySelector('.card-body') || document.body, r.t, function () {
            for (var m = 0; m < ids.length; m++) precos[m].value = original['preco_' + ids[m].value];
            Rascunho.apagar(k);
        });
    }
});
