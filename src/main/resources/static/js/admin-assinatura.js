document.addEventListener('DOMContentLoaded', () => {
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);
});

function copiarPix() {
    let input = document.getElementById("pixCopiaCola");
    input.select();
    document.execCommand("copy");
    alert("Código PIX copiado com sucesso!");
}

function confirmarCancelamento() {
    if(confirm("Tem certeza que deseja cancelar sua assinatura? O acesso ao sistema será suspenso no fim do ciclo vigente e todos os painéis ficarão bloqueados. Deseja continuar?")) {
        alert("Solicitação registrada. (Esta ação será interligada com a API do Stripe/Asaas)");
    }
}