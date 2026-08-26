document.addEventListener('DOMContentLoaded', () => {
    // Aplica o tema
    const currentTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', currentTheme);

    // ORDENAÇÃO ALFABÉTICA SEGURA
    try {
        let tbody = document.getElementById("tabelaMateriais");
        if (tbody) {
            let linhas = Array.from(tbody.querySelectorAll("tr:not(#msgVazia)"));

            linhas.sort((a, b) => {
                let elementoA = a.querySelector(".nome-item");
                let elementoB = b.querySelector(".nome-item");

                let nomeA = elementoA ? elementoA.innerText.trim().toLowerCase() : "";
                let nomeB = elementoB ? elementoB.innerText.trim().toLowerCase() : "";

                return nomeA.localeCompare(nomeB);
            });

            linhas.forEach(linha => tbody.appendChild(linha));
        }
    } catch (erro) {
        console.log("Erro na ordenação da tabela.", erro);
    }

    // Adiciona o evento de digitação na barra de busca (Sem sujar o HTML)
    const campoBusca = document.getElementById("campoBusca");
    if(campoBusca) {
        campoBusca.addEventListener("keyup", filtrarTabela);
    }
});

// Função que esconde as linhas que não combinam com a busca
function filtrarTabela() {
    let input = document.getElementById("campoBusca").value.toLowerCase();
    let linhas = document.querySelectorAll("#tabelaMateriais tr:not(#msgVazia)");
    let temResultado = false;

    linhas.forEach(linha => {
        let elementoNome = linha.querySelector(".nome-item");
        if (elementoNome) {
            let nomeItem = elementoNome.innerText.toLowerCase();
            if (nomeItem.includes(input)) {
                linha.style.display = "";
                temResultado = true;
            } else {
                linha.style.display = "none";
            }
        }
    });

    document.getElementById("msgVazia").style.display = temResultado ? "none" : "";
}