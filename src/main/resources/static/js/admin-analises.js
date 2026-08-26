document.addEventListener('DOMContentLoaded', () => {
    // 1. Aplica o Tema
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);

    // 2. Configura as cores base do gráfico (Chart.js) dependendo do tema
    Chart.defaults.color = currentTheme === 'dark' ? '#dee2e6' : '#212529';
    Chart.defaults.borderColor = currentTheme === 'dark' ? '#495057' : '#e9ecef';

    const cores = ['#0d6efd', '#198754', '#ffc107', '#dc3545', '#6610f2', '#fd7e14', '#20c997', '#adb5bd'];

    // 3. Monta o Gráfico de Rosca lendo a variável global deixada no HTML
    if(typeof dataKgFromServer !== 'undefined' && dataKgFromServer && Object.keys(dataKgFromServer).length > 0) {
        new Chart(document.getElementById('graficoKg').getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: Object.keys(dataKgFromServer),
                datasets: [{
                    data: Object.values(dataKgFromServer),
                    backgroundColor: cores
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'bottom' }
                }
            }
        });
    }
});

// 4. Lógica de Compartilhamento de WhatsApp
function compartilharReciclometro() {
    let totalElement = document.querySelector('#cardReciclometro h2');
    if(!totalElement) return;

    let total = totalElement.innerText;
    let msg = `♻️ *O Armazém Coletaê já retirou ${total} de materiais das ruas!*\n\n🏆 *Nosso Top 5 Histórico:* \n`;

    let items = document.querySelectorAll('#cardReciclometro li');
    items.forEach(li => {
        let nome = li.children[0].innerText.trim();
        let peso = li.children[1].innerText.trim();
        msg += `- ${nome}: ${peso}\n`;
    });

    msg += `\nFaça sua parte! Traga sua reciclagem para nós e ajude a limpar o mundo. 🌍💚`;

    let zapUrl = `https://api.whatsapp.com/send?text=${encodeURIComponent(msg)}`;
    window.open(zapUrl, '_blank');
}