// Função global para inicializar o tradutor do Google
function googleTranslateElementInit() {
    new google.translate.TranslateElement({pageLanguage: 'pt', autoDisplay: false}, 'google_translate_element');
}

// Gerenciador de troca de idiomas no Cookie
function trocarIdioma(lang) {
    if(lang === 'pt') {
        document.cookie = "googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
        document.cookie = "googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; domain=" + window.location.hostname + ";";
    } else {
        document.cookie = "googtrans=/pt/" + lang + "; path=/;";
        document.cookie = "googtrans=/pt/" + lang + "; path=/; domain=" + window.location.hostname + ";";
    }
    window.location.reload();
}

// Leitura do Cookie para atualizar a bandeira ao carregar a página
document.addEventListener("DOMContentLoaded", () => {
    let match = document.cookie.match(/googtrans=\/pt\/([a-zA-Z\-]+)/);
    let currentLang = match ? match[1] : 'pt';

    const flags = {
        'pt': { img: 'https://flagcdn.com/w20/br.png', text: 'PT (BR)' },
        'pt-PT': { img: 'https://flagcdn.com/w20/pt.png', text: 'PT (PT)' },
        'en': { img: 'https://flagcdn.com/w20/us.png', text: 'EN' },
        'es': { img: 'https://flagcdn.com/w20/es.png', text: 'ES' },
        'fr': { img: 'https://flagcdn.com/w20/fr.png', text: 'FR' }
    };

    if(flags[currentLang]) {
        let langImg = document.getElementById('currentLangImg');
        let langText = document.getElementById('currentLangText');

        if (langImg && langText) {
            langImg.src = flags[currentLang].img;
            langText.innerText = flags[currentLang].text;
        }
    }

    // Mantém a funcionalidade da máscara do WhatsApp
    const telInput = document.getElementById("telZap");
    if(telInput) {
        telInput.addEventListener("input", (e) => {
            let x = e.target.value.replace(/\D/g, '').match(/(\d{0,2})(\d{0,5})(\d{0,4})/);
            e.target.value = !x[2] ? x[1] : '(' + x[1] + ') ' + x[2] + (x[3] ? '-' + x[3] : '');
        });
    }
});

// ==========================================
// FUNÇÃO DE AGENDAMENTO (COM FILTRO DE NÚMERO PARA O WHATSAPP)
// ==========================================
function enviarZap(event) {
    event.preventDefault();

    let nome = document.getElementById("nomeZap").value.trim();
    let tel = document.getElementById("telZap").value.trim();
    let end = document.getElementById("endZap").value.trim();

    let combo = document.getElementById("armazemDestino");
    if (!combo.value) {
        alert("Selecione o estado, o município e o armazém para agendar a coleta.");
        return;
    }
    let nomeArmazem = combo.options[combo.selectedIndex].text;

    // Pega o telefone sujo que veio do banco de dados, ex: "(87) 99191-9496"
    let telefoneBruto = combo.options[combo.selectedIndex].getAttribute("data-telefone");

    let numeroDestino = "";

    if (telefoneBruto) {
        // MÁGICA: Arranca tudo que não for número (tira parênteses, traços, espaços)
        numeroDestino = telefoneBruto.replace(/\D/g, '');

        // Adiciona o código do Brasil (55) se o número tiver 10 ou 11 dígitos
        if (numeroDestino.length === 10 || numeroDestino.length === 11) {
            numeroDestino = "55" + numeroDestino;
        }
    } else {
        // Número de emergência caso o armazém não tenha cadastrado telefone
        numeroDestino = "5500000000000";
    }

    // Monta a mensagem incluindo o telefone do próprio catador para o armazém saber quem é
    let msg = `Olá! Me chamo *${nome}* e desejo agendar uma coleta em *${end}*. Meu contato é ${tel}. Selecionei o armazém *${nomeArmazem}* no site e vi os preços de hoje!`;

    // Usa a API oficial de envio (api.whatsapp.com/send?phone=) que não dá erro 404 com números formatados
    let zapUrl = `https://api.whatsapp.com/send?phone=${numeroDestino}&text=${encodeURIComponent(msg)}`;

    // Abre a aba do WhatsApp
    window.open(zapUrl, '_blank');

    // Envia o formulário para o Java salvar no painel interno do armazém
    setTimeout(() => { event.target.submit(); }, 300);
}


// ==========================================
// FILTRO POR REGIÃO: Estado > Município > Armazém
// ==========================================
const NOMES_UF = {AC:"Acre",AL:"Alagoas",AP:"Amapá",AM:"Amazonas",BA:"Bahia",CE:"Ceará",DF:"Distrito Federal",ES:"Espírito Santo",GO:"Goiás",MA:"Maranhão",MT:"Mato Grosso",MS:"Mato Grosso do Sul",MG:"Minas Gerais",PA:"Pará",PB:"Paraíba",PR:"Paraná",PE:"Pernambuco",PI:"Piauí",RJ:"Rio de Janeiro",RN:"Rio Grande do Norte",RS:"Rio Grande do Sul",RO:"Rondônia",RR:"Roraima",SC:"Santa Catarina",SP:"São Paulo",SE:"Sergipe",TO:"Tocantins"};

function chaveTexto(t) {
    return (t || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim().toLowerCase();
}

function iniciarFiltroRegiao() {
    const selUf = document.getElementById("ufZap");
    const selCidade = document.getElementById("cidadeZap");
    const selArmazem = document.getElementById("armazemDestino");
    if (!selUf || !selCidade || !selArmazem) return;

    // guarda as opções originais (com telefone do armazém) antes de filtrar
    const todos = Array.from(selArmazem.options).slice(1).map(o => ({
        el: o,
        nome: o.textContent,
        uf: (o.dataset.uf || "").toUpperCase(),
        cidade: o.dataset.cidade || ""
    }));
    // sem nenhum armazém disponível: deixa a mensagem clara em vez de campos vazios
    if (todos.length === 0) {
        [selUf, selCidade, selArmazem].forEach(select => {
            select.innerHTML = "";
            const o = document.createElement("option");
            o.value = "";
            o.disabled = true;
            o.selected = true;
            o.textContent = "Nenhum armazém disponível no momento";
            select.appendChild(o);
            select.disabled = true;
        });
        return;
    }
    const semRegiao = todos.filter(a => !a.uf || !a.cidade);
    const comRegiao = todos.filter(a => a.uf && a.cidade);

    function limpar(select, rotulo) {
        select.innerHTML = "";
        const ph = document.createElement("option");
        ph.value = "";
        ph.disabled = true;
        ph.selected = true;
        ph.textContent = rotulo;
        select.appendChild(ph);
    }
    function addOpcao(select, valor, texto) {
        const o = document.createElement("option");
        o.value = valor;
        o.textContent = texto;
        select.appendChild(o);
    }
    function mostrarCidade(visivel) {
        const caixa = selCidade.closest(".form-floating");
        if (caixa) caixa.classList.toggle("d-none", !visivel);
        selCidade.required = visivel;
    }
    function mostrarArmazens(lista) {
        limpar(selArmazem, lista.length ? "Selecione um Armazém..." : "Nenhum armazém nessa região");
        lista.sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR")).forEach(a => selArmazem.appendChild(a.el));
    }

    function aoMudarCidade() {
        const chave = selCidade.value;
        mostrarArmazens(comRegiao.filter(a => a.uf === selUf.value && chaveTexto(a.cidade) === chave));
    }

    function aoMudarUf() {
        const uf = selUf.value;
        limpar(selCidade, "Selecione o município...");
        limpar(selArmazem, "Selecione um Armazém...");
        if (uf === "__SEM__") {            // armazéns antigos, sem região cadastrada
            mostrarCidade(false);
            mostrarArmazens(semRegiao.slice());
            return;
        }
        mostrarCidade(true);
        if (!uf) return;
        const cidades = new Map();
        comRegiao.filter(a => a.uf === uf).forEach(a => {
            const k = chaveTexto(a.cidade);
            if (!cidades.has(k)) cidades.set(k, a.cidade.trim());
        });
        const lista = Array.from(cidades.entries()).sort((a, b) => a[1].localeCompare(b[1], "pt-BR"));
        lista.forEach(([k, nome]) => addOpcao(selCidade, k, nome));
        if (lista.length === 1) {
            selCidade.value = lista[0][0];
            aoMudarCidade();
        }
    }

    // monta a lista de estados só com os que têm armazém cadastrado
    limpar(selUf, "Selecione o estado...");
    const ufs = Array.from(new Set(comRegiao.map(a => a.uf)))
        .sort((a, b) => (NOMES_UF[a] || a).localeCompare(NOMES_UF[b] || b, "pt-BR"));
    ufs.forEach(uf => addOpcao(selUf, uf, (NOMES_UF[uf] || uf) + " (" + uf + ")"));
    if (semRegiao.length) addOpcao(selUf, "__SEM__", "Outros (região não informada)");

    limpar(selCidade, "Selecione o município...");
    limpar(selArmazem, "Selecione um Armazém...");
    selUf.required = true;
    selCidade.required = true;

    selUf.addEventListener("change", aoMudarUf);
    selCidade.addEventListener("change", aoMudarCidade);

    if (ufs.length === 1 && !semRegiao.length) {
        selUf.value = ufs[0];
        aoMudarUf();
    }
}

document.addEventListener("DOMContentLoaded", iniciarFiltroRegiao);
