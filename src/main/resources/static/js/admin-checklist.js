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
        registrarLote(id, valorParcial);
        if (window.salvarRascunhoPesagem) salvarRascunhoPesagem();
        atualizarResumo();
    }
}

function zerarTotal(id) {
    document.getElementById('total_' + id).value = '';
    document.getElementById('parcial_' + id).value = '';
    const lotes = document.getElementById('lotes_' + id);
    if (lotes) { lotes.textContent = ''; lotes.dataset.lista = ''; }
    if (window.salvarRascunhoPesagem) salvarRascunhoPesagem();
    atualizarResumo();
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


// ==========================================
// Fase 31: busca, filtro "já pesados", Enter para somar, histórico dos lotes e resumo sempre visível
// ==========================================
const fmtBR = (n, casas) => Number(n).toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas });
const semAcento = t => (t || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

function registrarLote(id, valor) {
    const el = document.getElementById('lotes_' + id);
    if (!el) return;
    const lista = (el.dataset.lista ? el.dataset.lista.split('|') : []);
    lista.push(fmtBR(valor, 2));
    el.dataset.lista = lista.join('|');
    el.textContent = 'Lotes: ' + lista.join(' + ');
}

function atualizarResumo() {
    let kg = 0, un = 0, valor = 0, pesados = 0;
    const ul = document.getElementById('resumoItens');
    if (!ul) return;
    ul.querySelectorAll('li.item').forEach(li => li.remove());
    document.querySelectorAll('.mat-linha').forEach(linha => {
        const id = linha.dataset.id;
        const total = parseFloat((document.getElementById('total_' + id) || {}).value) || 0;
        linha.classList.toggle('pesado', total > 0);
        if (total <= 0) return;
        pesados++;
        const preco = parseFloat(linha.dataset.preco) || 0;
        const ehUn = (linha.dataset.un || '').toUpperCase() === 'UN';
        if (ehUn) un += total; else kg += total;
        const sub = Math.round(total * preco * 100) / 100;
        valor += sub;
        const li = document.createElement('li');
        li.className = 'item d-flex justify-content-between gap-2 py-1 border-bottom';
        const nome = document.createElement('span');
        nome.className = 'text-truncate';
        nome.textContent = linha.dataset.nome + ' · ' + fmtBR(total, 2) + (ehUn ? ' un' : ' kg');
        const v = document.createElement('span');
        v.className = 'fw-bold text-nowrap';
        v.textContent = 'R$ ' + fmtBR(sub, 2);
        li.appendChild(nome); li.appendChild(v);
        ul.appendChild(li);
    });
    const vazio = document.getElementById('resumoVazio');
    if (vazio) vazio.classList.toggle('d-none', pesados > 0);
    const set = (id, t) => { const e = document.getElementById(id); if (e) e.textContent = t; };
    set('resumoKg', fmtBR(kg, 2) + ' kg');
    set('resumoUn', fmtBR(un, un % 1 === 0 ? 0 : 2) + ' un');
    set('resumoValor', 'R$ ' + fmtBR(valor, 2));
    set('resumoValorCel', 'R$ ' + fmtBR(valor, 2));
    set('contPesados', pesados);
    filtrarMateriais();
}

function filtrarMateriais() {
    const busca = document.getElementById('buscaMaterial');
    const soPesados = (document.getElementById('fPesados') || {}).checked;
    const termo = semAcento(busca ? busca.value.trim() : '');
    let algum = false;
    document.querySelectorAll('.mat-linha').forEach(linha => {
        const mostra = (!termo || semAcento(linha.dataset.nome).includes(termo)) && (!soPesados || linha.classList.contains('pesado'));
        linha.classList.toggle('d-none', !mostra);
        if (mostra) algum = true;
    });
    const nada = document.getElementById('nadaEncontrado');
    if (nada) {
        nada.classList.toggle('d-none', algum);
        nada.textContent = soPesados && !termo ? 'Nenhum material pesado ainda.' : 'Nenhum material com esse nome.';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const busca = document.getElementById('buscaMaterial');
    if (busca) busca.addEventListener('input', filtrarMateriais);
    ['fTodos', 'fPesados'].forEach(id => { const r = document.getElementById(id); if (r) r.addEventListener('change', filtrarMateriais); });

    // Enter no peso do lote = Somar (não envia o formulário)
    document.querySelectorAll('.campo-lote').forEach(campo => {
        campo.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') { e.preventDefault(); adicionarAoTotal(campo.id.split('_')[1]); }
        });
    });
    // "/" leva para a busca
    document.addEventListener('keydown', (e) => {
        if (e.key === '/' && busca && document.activeElement && document.activeElement.tagName !== 'INPUT') { e.preventDefault(); busca.focus(); }
    });
    // Enter na busca: vai direto para o peso do primeiro material encontrado
    if (busca) busca.addEventListener('keydown', (e) => {
        if (e.key !== 'Enter') return;
        e.preventDefault();
        const primeira = document.querySelector('.mat-linha:not(.d-none) .campo-lote');
        if (primeira) primeira.focus();
    });
    atualizarResumo(); // também cobre o rascunho restaurado
});
