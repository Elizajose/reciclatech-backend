document.addEventListener('DOMContentLoaded', () => {

    // ==========================================
    // 1. LÓGICA DO TEMA (LIGHT/DARK)
    // ==========================================
    const htmlElement = document.documentElement;
    const themeToggleBtn = document.getElementById('themeToggleBtn');
    const themeIcon = document.getElementById('themeIcon');

    // Recupera a preferência salva ou define 'light' como padrão
    const currentTheme = localStorage.getItem('theme') || 'light';

    // Aplica o tema na inicialização
    htmlElement.setAttribute('data-bs-theme', currentTheme);
    updateIcon(currentTheme);

    // Evento de clique para o botão de troca de tema (se existir na tela)
    if (themeToggleBtn) {
        themeToggleBtn.addEventListener('click', (e) => {
            e.stopPropagation(); // Evita que o menu dropdown feche ao trocar o tema

            const isDark = htmlElement.getAttribute('data-bs-theme') === 'dark';
            const newTheme = isDark ? 'light' : 'dark';

            htmlElement.setAttribute('data-bs-theme', newTheme);
            localStorage.setItem('theme', newTheme);
            updateIcon(newTheme);
        });
    }

    // Função que troca apenas o ícone de Sol/Lua
    function updateIcon(theme) {
        if (!themeIcon) return;

        if (theme === 'dark') {
            themeIcon.classList.remove('bi-moon-fill');
            themeIcon.classList.add('bi-sun-fill', 'text-warning');
        } else {
            themeIcon.classList.remove('bi-sun-fill', 'text-warning');
            themeIcon.classList.add('bi-moon-fill');
        }
    }

    // ==========================================
    // 2. MÁSCARA DE CPF PARA O BALCÃO
    // ==========================================
    const cpfInput = document.getElementById('cpfInput');
    if (cpfInput) {
        cpfInput.addEventListener('input', function() {
            let v = this.value.replace(/\D/g, ""); // Remove tudo que não é número
            if (v.length > 11) v = v.substring(0, 11); // Trava em exatos 11 números

            // Aplica a formatação 000.000.000-00 visualmente
            if (v.length > 9) {
                v = v.replace(/(\d{3})(\d{3})(\d{3})(\d{1,2})/, "$1.$2.$3-$4");
            } else if (v.length > 6) {
                v = v.replace(/(\d{3})(\d{3})(\d{1,3})/, "$1.$2.$3");
            } else if (v.length > 3) {
                v = v.replace(/(\d{3})(\d{1,3})/, "$1.$2");
            }

            this.value = v;
        });
    }
});
// ==========================================
// Fase 29: saudação do painel e troca de senha
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
    // "Bom dia / Boa tarde / Boa noite" e a data de hoje (relógio do aparelho)
    const saudacao = document.getElementById('painelSaudacao');
    const data = document.getElementById('painelData');
    const agora = new Date();
    if (saudacao) {
        const h = agora.getHours();
        saudacao.textContent = h < 5 ? 'Boa noite' : (h < 12 ? 'Bom dia' : (h < 18 ? 'Boa tarde' : 'Boa noite'));
    }
    if (data) {
        try {
            const t = agora.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
            data.textContent = t.charAt(0).toUpperCase() + t.slice(1);
        } catch (e) { /* navegador antigo: fica sem a data */ }
    }

    const modal = document.getElementById('modalAlterarSenhas');
    const form = document.getElementById('formTrocarSenha');
    if (!modal || !form) return;
    const atual = document.getElementById('senhaAtual');
    const nova = document.getElementById('novaSenha');
    const repete = document.getElementById('confirmarSenha');
    const mostrar = document.getElementById('mostrarSenhas');
    const erro = document.getElementById('erroSenhaCliente');

    function mostrarErro(texto, campo) {
        erro.textContent = texto;
        erro.classList.remove('d-none');
        if (campo) campo.focus();
    }

    if (mostrar) {
        mostrar.addEventListener('change', () => {
            [atual, nova, repete].forEach(c => { if (c) c.type = mostrar.checked ? 'text' : 'password'; });
        });
    }

    form.addEventListener('submit', (e) => {
        erro.classList.add('d-none');
        if (!atual.value) { e.preventDefault(); mostrarErro('Digite a sua senha atual.', atual); return; }
        if (nova.value.length < 8) { e.preventDefault(); mostrarErro('A nova senha deve ter pelo menos 8 caracteres.', nova); return; }
        if (nova.value.length > 72) { e.preventDefault(); mostrarErro('A nova senha deve ter no máximo 72 caracteres.', nova); return; }
        if (nova.value !== repete.value) { e.preventDefault(); mostrarErro('A repetição não confere com a nova senha.', repete); return; }
        if (nova.value === atual.value) { e.preventDefault(); mostrarErro('A nova senha é igual à atual. Escolha outra.', nova); return; }
        const botao = form.querySelector('button[type=submit]');
        if (botao) { botao.disabled = true; botao.textContent = 'Trocando...'; }
    });

    // voltou do servidor com erro: reabre a janela já mostrando o motivo
    if (modal.dataset.abrir === 'sim' && window.bootstrap && bootstrap.Modal) {
        bootstrap.Modal.getOrCreateInstance(modal).show();
    }
    modal.addEventListener('shown.bs.modal', () => { if (atual) atual.focus(); });
});

// ==========================================
// Fase 30: Central de Ajuda (busca nos tutoriais, contador e envio do chamado)
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
    const semAcento = t => (t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

    const busca = document.getElementById('buscaAjuda');
    const nada = document.getElementById('ajudaNada');
    if (busca) {
        busca.addEventListener('input', () => {
            const termo = semAcento(busca.value.trim());
            let algum = false;
            document.querySelectorAll('#accordionFaq .accordion-item').forEach(item => {
                const mostra = !termo || semAcento(item.textContent).includes(termo);
                item.classList.toggle('d-none', !mostra);
                if (mostra) algum = true;
            });
            if (nada) nada.classList.toggle('d-none', algum);
        });
    }

    // links "abra um chamado" dentro da ajuda trocam de aba
    document.querySelectorAll('[data-ir-aba]').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const botao = document.querySelector('#ajudaTabs [data-bs-target="' + link.dataset.irAba + '"]');
            if (botao && window.bootstrap && bootstrap.Tab) bootstrap.Tab.getOrCreateInstance(botao).show();
        });
    });

    const descricao = document.getElementById('chamadoDescricao');
    const contador = document.getElementById('chamadoContador');
    if (descricao && contador) {
        const atualizar = () => { contador.textContent = descricao.value.length; };
        descricao.addEventListener('input', atualizar);
        atualizar();
    }

    const formChamado = document.getElementById('formChamado');
    if (formChamado) {
        formChamado.addEventListener('submit', (e) => {
            const arquivo = document.getElementById('chamadoPrint');
            if (arquivo && arquivo.files && arquivo.files[0] && arquivo.files[0].size > 5 * 1024 * 1024) {
                e.preventDefault();
                alert('O print passa de 5 MB. Escolha uma imagem menor.');
                return;
            }
            const botao = formChamado.querySelector('button[type=submit]');
            if (botao) { botao.disabled = true; botao.textContent = 'Enviando...'; }
        });
    }
});
