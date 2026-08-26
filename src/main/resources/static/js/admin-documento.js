document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});

function gerarQRCode() {
    const qrcodeElement = document.getElementById("qrcode");
    if (!qrcodeElement) return;

    const urlBase = window.location.origin + window.location.pathname;
    const linkResumo = urlBase + "?view=resumo";

    new QRCode(qrcodeElement, {
        text: linkResumo,
        width: 85,
        height: 85,
        colorDark : "#000000",
        colorLight : "#ffffff",
        correctLevel : QRCode.CorrectLevel.H
    });
}

function enviarZap(destino) {
    let nomeInput = document.getElementById("jsNome");
    let totalInput = document.getElementById("jsTotal");
    let telefoneInput = document.getElementById("jsTelefone");

    if (!nomeInput || !totalInput || !telefoneInput) return;

    let nome = nomeInput.value;
    let total = totalInput.value;
    let telefoneCliente = telefoneInput.value;

    const urlBase = window.location.origin + window.location.pathname;
    let linkRecibo = urlBase + "?view=resumo";

    let msg = `🧾 *DOCUMENTO COLETAÊ SALGUEIRO*\n\nOlá *${nome}*,\nO registro no valor de *R$ ${total}* foi processado.\n\n🔗 *Documento Digital:* ${linkRecibo}\n\n_Obrigado por usar a nossa plataforma!_`;

    let numeroDestino = (destino === 'empresa') ? "5587991919496" : "55" + telefoneCliente;
    window.open(`https://api.whatsapp.com/send?phone=${numeroDestino}&text=${encodeURIComponent(msg)}`, '_blank');
}

window.onload = function() {
    gerarQRCode();

    const params = new URLSearchParams(window.location.search);
    // Dispara a impressão automática se o sistema pedir e NÃO estiver no modo cliente
    if (params.get('autoPrint') === 'true' && params.get('view') !== 'resumo') {
        setTimeout(() => { window.print(); }, 800);
    }
};