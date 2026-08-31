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