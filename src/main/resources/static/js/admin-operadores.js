// Fase 30: Gestão de Equipe — copiar código, sugerir usuário, mostrar senha e ocultar dados
(function () {
    "use strict";

    // Copiar o código do armazém
    const copiar = document.getElementById("copiarCodigo");
    const codigo = document.getElementById("codigoArmazem");
    if (copiar && codigo) {
        copiar.addEventListener("click", async () => {
            const texto = codigo.textContent.trim();
            let ok = false;
            try { await navigator.clipboard.writeText(texto); ok = true; } catch (e) {
                const r = document.createRange(); r.selectNodeContents(codigo);
                const s = window.getSelection(); s.removeAllRanges(); s.addRange(r);
                try { ok = document.execCommand("copy"); } catch (e2) { ok = false; }
            }
            const rotulo = copiar.querySelector("span");
            if (rotulo) {
                rotulo.textContent = ok ? "Copiado!" : "Selecione e copie";
                setTimeout(() => { rotulo.textContent = "Copiar"; }, 2000);
            }
        });
    }

    // Sugere o usuário a partir do nome ("João da Silva" -> "joao.silva"), enquanto o usuário não for editado à mão
    const nome = document.getElementById("opNome");
    const login = document.getElementById("opLogin");
    if (nome && login) {
        let editado = false;
        login.addEventListener("input", () => { editado = login.value.trim() !== ""; });
        nome.addEventListener("input", () => {
            if (editado) return;
            const partes = nome.value.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase()
                .replace(/[^a-z0-9 ]/g, "").split(/\s+/).filter(p => p && !["da", "de", "do", "das", "dos", "e"].includes(p));
            login.value = partes.length === 0 ? "" : (partes.length === 1 ? partes[0] : partes[0] + "." + partes[partes.length - 1]).slice(0, 40);
        });
    }

    // Bloquear pede confirmação (liberar não)
    document.querySelectorAll("form.form-status").forEach(f => {
        f.addEventListener("submit", (e) => {
            if (f.dataset.confirmar === "true" && !confirm("Bloquear o acesso deste operador? Ele sai do sistema em instantes.")) e.preventDefault();
        });
    });

    // Mostrar / esconder a senha
    const ver = document.getElementById("verSenhaOp");
    const senha = document.getElementById("opSenha");
    if (ver && senha) {
        ver.addEventListener("click", () => {
            const mostrar = senha.type === "password";
            senha.type = mostrar ? "text" : "password";
            const i = ver.querySelector("i");
            if (i) i.className = mostrar ? "bi bi-eye-slash" : "bi bi-eye";
        });
    }
})();

// Ocultar nomes e usuários (para mostrar a tela a alguém)
let dataHidden = false;
function togglePrivacy() {
    dataHidden = !dataHidden;
    document.querySelectorAll(".operator-field").forEach(el => {
        el.textContent = dataHidden ? "••••••••" : el.getAttribute("data-real");
    });
    const icon = document.getElementById("privacyIcon");
    const texto = document.getElementById("privacyText");
    if (icon) icon.className = dataHidden ? "bi bi-eye" : "bi bi-eye-slash";
    if (texto) texto.textContent = dataHidden ? "Mostrar dados" : "Ocultar dados";
}
