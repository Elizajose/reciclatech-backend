// Recibo: tema, QR Code de verificação (Fase 31: aponta para /recibo/CÓDIGO, que abre sem login) e impressão automática
document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});

function gerarQRCode() {
    const el = document.getElementById("qrcode");
    if (!el || !el.dataset.url || typeof QRCode === "undefined") return;
    let url = el.dataset.url;
    if (url.startsWith("/")) url = window.location.origin + url; // sem URL_BASE configurada: usa o endereço atual
    new QRCode(el, { text: url, width: 78, height: 78, colorDark: "#000000", colorLight: "#ffffff", correctLevel: QRCode.CorrectLevel.M });
}

function enviarZap(destino) {
    const nome = (document.getElementById("jsNome") || {}).value || "";
    const total = (document.getElementById("jsTotal") || {}).value || "";
    const telefoneCliente = (document.getElementById("jsTelefone") || {}).value || "";
    const armazem = (document.getElementById("jsArmazem") || {}).value || "Coletaê";
    const qr = document.getElementById("qrcode");
    const link = qr && qr.dataset.url ? (qr.dataset.url.startsWith("/") ? window.location.origin + qr.dataset.url : qr.dataset.url) : "";
    let msg = `🧾 *${armazem}*\n\nOlá *${nome}*, o seu recibo no valor de *R$ ${total}* foi registrado.`;
    if (link) msg += `\n\n🔗 Confira: ${link}`;
    const numeroDestino = (destino === 'empresa') ? "5587991919496" : "55" + telefoneCliente.replace(/\D/g, "");
    window.open(`https://api.whatsapp.com/send?phone=${numeroDestino}&text=${encodeURIComponent(msg)}`, '_blank');
}

window.onload = function () {
    gerarQRCode();
    const params = new URLSearchParams(window.location.search);
    if (params.get('autoPrint') === 'true' && params.get('view') !== 'resumo') {
        setTimeout(() => { window.print(); }, 800);
    }
};
