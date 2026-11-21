// =====================================================================
// Fase 27: cotação da página inicial para muitos armazéns.
//  1) Região primeiro: só mostra armazéns depois de escolher Estado e cidade (lembra a última escolha).
//  2) Busca pelo nome do armazém enquanto digita.
//  3) "Quem paga mais": ranking de um material na cidade, com o melhor preço destacado.
//  4) Cartão enxuto: 5 materiais + "Ver todos" (com busca), "atualizado há X dias", "Ver mais armazéns".
// O servidor devolve só o que foi pedido (rotas /cotacoes/...). Todo texto vindo do servidor entra com textContent.
// =====================================================================
(function () {
    "use strict";

    const CHAVE_SALVA = "coletae.cotacao";
    const SEM_REGIAO = "__SEM__";
    const MAX_CHIPS = 8;
    const nomesUf = (typeof NOMES_UF !== "undefined") ? NOMES_UF : {};

    const estado = { uf: "", cidade: "", visao: "armazem", busca: "", pagina: 0, material: "" };
    let regioes = null;
    let pedidoAtual = 0;          // descarta respostas atrasadas (quem digita rápido na busca)
    let esperaBusca = null;

    const $ = (id) => document.getElementById(id);

    // ------------------------------------------------------------ utilidades
    function ler() {
        try { return JSON.parse(localStorage.getItem(CHAVE_SALVA) || "null") || {}; } catch (e) { return {}; }
    }
    function gravar() {
        try {
            localStorage.setItem(CHAVE_SALVA, JSON.stringify({
                uf: estado.uf, cidade: estado.cidade, visao: estado.visao, material: estado.material
            }));
        } catch (e) { /* sem localStorage: só não lembra a escolha */ }
    }

    function el(tag, classe, texto) {
        const e = document.createElement(tag);
        if (classe) e.className = classe;
        if (texto !== undefined && texto !== null) e.textContent = texto;
        return e;
    }

    function opcao(select, valor, texto, selecionada) {
        const o = document.createElement("option");
        o.value = valor;
        o.textContent = texto;
        if (selecionada) o.selected = true;
        select.appendChild(o);
        return o;
    }

    function placeholder(select, texto) {
        select.innerHTML = "";
        const o = opcao(select, "", texto, true);
        o.disabled = true;
    }

    function dinheiro(v) {
        const n = Number(v);
        if (!isFinite(n)) return "-";
        return n.toLocaleString("pt-BR", { style: "currency", currency: "BRL", minimumFractionDigits: 2, maximumFractionDigits: 3 });
    }

    /** "hoje", "ontem", "há 5 dias"; nulo = não sabemos. */
    function textoDias(d) {
        if (d === null || d === undefined) return null;
        if (d <= 0) return "hoje";
        if (d === 1) return "ontem";
        return "há " + d + " dias";
    }

    function seloDias(d, prefixo) {
        const t = textoDias(d);
        if (!t) return null;
        const antigo = d > 30;
        const s = el("span", "cot-dias small " + (antigo ? "text-warning-emphasis" : "text-muted"));
        const i = el("i", "bi " + (antigo ? "bi-exclamation-circle" : "bi-clock") + " me-1");
        s.appendChild(i);
        s.appendChild(document.createTextNode(prefixo + t + (antigo ? " (pode estar desatualizado)" : "")));
        return s;
    }

    async function buscarJson(caminho, params) {
        const url = new URL(caminho, window.location.origin);
        Object.keys(params || {}).forEach(k => {
            if (params[k] !== undefined && params[k] !== null && params[k] !== "") url.searchParams.set(k, params[k]);
        });
        const r = await fetch(url.toString(), { credentials: "same-origin", headers: { "Accept": "application/json" } });
        let corpo = null;
        try { corpo = await r.json(); } catch (e) { corpo = null; }
        if (!r.ok) throw new Error((corpo && corpo.erro) || "Não foi possível carregar os preços agora.");
        return corpo;
    }

    function regiaoParams() {
        return estado.uf === SEM_REGIAO ? { uf: SEM_REGIAO } : { uf: estado.uf, cidade: estado.cidade };
    }

    function aviso(texto, icone) {
        $("cotAviso").classList.remove("d-none");
        $("cotAvisoTexto").textContent = texto;
        const i = $("cotAviso").querySelector("i");
        if (i) i.className = "bi " + (icone || "bi-geo-alt") + " fs-2 d-block mb-2 text-success";
        $("cotPainelArmazem").classList.add("d-none");
        $("cotPainelMaterial").classList.add("d-none");
    }

    function falha(conteiner, mensagem, tentarDeNovo) {
        conteiner.innerHTML = "";
        const caixa = el("div", "col-12 text-center text-muted py-4");
        caixa.appendChild(el("p", "mb-2", mensagem || "Não foi possível carregar os preços agora."));
        const b = el("button", "btn btn-sm btn-outline-success rounded-pill", "Tentar de novo");
        b.type = "button";
        b.addEventListener("click", tentarDeNovo);
        caixa.appendChild(b);
        conteiner.appendChild(caixa);
    }

    function carregando(conteiner) {
        conteiner.innerHTML = "";
        const c = el("div", "col-12 text-center text-muted py-4");
        c.appendChild(el("span", "spinner-border spinner-border-sm me-2"));
        c.appendChild(document.createTextNode("Carregando preços..."));
        conteiner.appendChild(c);
    }

    function botaoAgendar(id, classe) {
        const b = el("button", classe || "btn btn-sm btn-success rounded-pill fw-bold");
        b.type = "button";
        b.appendChild(el("i", "bi bi-whatsapp me-1"));
        b.appendChild(document.createTextNode("Agendar coleta"));
        b.addEventListener("click", () => {
            const ok = window.coletaeAgendar && window.coletaeAgendar.selecionarArmazem(id);
            if (!ok) {
                const caixa = $("agendar");
                if (caixa) caixa.scrollIntoView({ behavior: "smooth", block: "start" });
            }
        });
        return b;
    }

    // ------------------------------------------------------------ região
    function preencherUfs() {
        const sel = $("cotUf");
        placeholder(sel, "Selecione o estado...");
        const lista = (regioes.estados || []).slice()
            .sort((a, b) => (nomesUf[a.uf] || a.uf).localeCompare(nomesUf[b.uf] || b.uf, "pt-BR"));
        lista.forEach(e => opcao(sel, e.uf, (nomesUf[e.uf] || e.uf) + " (" + e.uf + ")"));
        if (regioes.semRegiao > 0) opcao(sel, SEM_REGIAO, "Outros (região não informada)");
        sel.disabled = false;
    }

    function cidadesDe(uf) {
        const e = (regioes.estados || []).find(x => x.uf === uf);
        return e ? e.cidades : [];
    }

    function preencherCidades(uf) {
        const sel = $("cotCidade");
        const caixa = $("cotCaixaCidade");
        if (uf === SEM_REGIAO) {
            caixa.classList.add("d-none");
            return [];
        }
        caixa.classList.remove("d-none");
        placeholder(sel, "Selecione a cidade...");
        const cidades = cidadesDe(uf).slice().sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
        cidades.forEach(c => opcao(sel, c.chave, c.nome + " (" + c.armazens + (c.armazens === 1 ? " armazém)" : " armazéns)")));
        sel.disabled = cidades.length === 0;
        return cidades;
    }

    function aoMudarUf() {
        estado.uf = $("cotUf").value;
        estado.cidade = "";
        estado.material = "";
        const cidades = preencherCidades(estado.uf);
        if (estado.uf === SEM_REGIAO) { regiaoPronta(); return; }
        $("cotVisoes").classList.add("d-none");
        aviso("Agora escolha a cidade.", "bi-buildings");
        if (cidades.length === 1) {
            $("cotCidade").value = cidades[0].chave;
            aoMudarCidade();
        } else {
            gravar();
        }
    }

    function aoMudarCidade() {
        estado.cidade = $("cotCidade").value;
        estado.material = "";
        regiaoPronta();
    }

    function regiaoPronta() {
        if (!estado.uf || (estado.uf !== SEM_REGIAO && !estado.cidade)) return;
        gravar();
        $("cotVisoes").classList.remove("d-none");
        $("cotAviso").classList.add("d-none");
        estado.busca = "";
        $("cotBusca").value = "";
        mostrarVisao(estado.visao);
    }

    // ------------------------------------------------------------ visões
    function mostrarVisao(visao) {
        estado.visao = visao === "material" ? "material" : "armazem";
        document.querySelectorAll("#cotVisoes [data-visao]").forEach(b => {
            const ativo = b.dataset.visao === estado.visao;
            b.classList.toggle("active", ativo);
            b.setAttribute("aria-pressed", ativo ? "true" : "false");
        });
        $("cotPainelArmazem").classList.toggle("d-none", estado.visao !== "armazem");
        $("cotPainelMaterial").classList.toggle("d-none", estado.visao !== "material");
        gravar();
        if (estado.visao === "armazem") carregarArmazens(true);
        else carregarMateriais();
    }

    // ---- por armazém
    async function carregarArmazens(doZero) {
        const lista = $("cotLista");
        const verMais = $("cotVerMais");
        if (doZero) { estado.pagina = 0; carregando(lista); }
        verMais.disabled = true;
        const meu = ++pedidoAtual;
        try {
            const dados = await buscarJson("/cotacoes/armazens",
                Object.assign(regiaoParams(), { busca: estado.busca, pagina: estado.pagina }));
            if (meu !== pedidoAtual) return;
            if (doZero) lista.innerHTML = "";
            dados.itens.forEach(a => lista.appendChild(cartaoArmazem(a, dados.principais || 5)));
            const resumo = $("cotResumo");
            if (dados.total === 0) {
                lista.innerHTML = "";
                lista.appendChild(el("div", "col-12 text-center text-muted py-4",
                    estado.busca ? "Nenhum armazém com “" + estado.busca + "” nesta região." : "Nenhum armazém nesta região."));
                resumo.textContent = "";
            } else {
                const mostrados = Math.min(dados.total, (dados.pagina + 1) * dados.porPagina);
                resumo.textContent = (estado.busca ? dados.total + " de " + dados.totalRegiao + " armazéns" : dados.total + (dados.total === 1 ? " armazém" : " armazéns"))
                    + (dados.total > mostrados ? " · mostrando " + mostrados : "") + " · ordem alfabética";
            }
            verMais.classList.toggle("d-none", !dados.temMais);
            verMais.disabled = false;
        } catch (e) {
            if (meu !== pedidoAtual) return;
            if (doZero) falha(lista, e.message, () => carregarArmazens(true));
            else { verMais.disabled = false; alert(e.message); }
        }
    }

    function linhaMaterial(m) {
        const li = el("li", "cot-mat d-flex justify-content-between align-items-center");
        li.dataset.busca = (m.nome || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
        const nome = el("span", "cot-mat-nome text-uppercase", m.nome);
        const preco = el("span", "cot-mat-preco notranslate");
        preco.appendChild(document.createTextNode(dinheiro(m.preco)));
        preco.appendChild(el("small", "text-muted fw-normal ms-1", "/" + (m.unidade || "KG").toLowerCase()));
        li.appendChild(nome);
        li.appendChild(preco);
        return li;
    }

    function cartaoArmazem(a, principais) {
        const col = el("div", "col-12 col-md-6 col-lg-4");
        const card = el("div", "cot-card h-100 d-flex flex-column");
        const topo = el("div", "d-flex align-items-start gap-2 mb-2");
        const icone = el("div", "cot-icone flex-shrink-0");
        icone.appendChild(el("i", "bi bi-shop"));
        const titulo = el("div", "flex-grow-1 min-w-0");
        titulo.appendChild(el("h3", "cot-nome notranslate mb-0", a.nome));
        const selo = seloDias(a.dias, "Última atualização: ");
        if (selo) titulo.appendChild(selo);
        topo.appendChild(icone);
        topo.appendChild(titulo);
        card.appendChild(topo);

        const mats = a.materiais || [];
        if (mats.length === 0) {
            card.appendChild(el("p", "small text-muted my-3", "Nenhum preço cadastrado ainda."));
        } else {
            const ul = el("ul", "cot-mats list-unstyled mb-2");
            mats.forEach((m, i) => {
                const li = linhaMaterial(m);
                if (i >= principais) li.classList.add("d-none", "cot-extra");
                ul.appendChild(li);
            });
            card.appendChild(ul);

            if (mats.length > principais) {
                const filtro = el("input", "form-control form-control-sm mb-2 d-none");
                filtro.type = "search";
                filtro.placeholder = "Buscar material neste armazém";
                filtro.setAttribute("aria-label", "Buscar material neste armazém");
                filtro.maxLength = 40;
                const nada = el("p", "small text-muted d-none mb-2", "Nenhum material com esse nome.");
                const botao = el("button", "btn btn-link btn-sm p-0 mb-2 text-success fw-bold text-decoration-none align-self-start",
                    "Ver todos os " + mats.length + " materiais");
                botao.type = "button";
                let aberto = false;
                botao.addEventListener("click", () => {
                    aberto = !aberto;
                    filtro.value = "";
                    nada.classList.add("d-none");
                    ul.querySelectorAll("li").forEach((li, i) => li.classList.toggle("d-none", !aberto && i >= principais));
                    filtro.classList.toggle("d-none", !aberto);
                    botao.textContent = aberto ? "Mostrar menos" : "Ver todos os " + mats.length + " materiais";
                    if (aberto) filtro.focus();
                });
                filtro.addEventListener("input", () => {
                    const t = filtro.value.normalize("NFD").replace(/[̀-ͯ]/g, "").trim().toLowerCase();
                    let algum = false;
                    ul.querySelectorAll("li").forEach(li => {
                        const mostra = !t || li.dataset.busca.indexOf(t) >= 0;
                        li.classList.toggle("d-none", !mostra);
                        if (mostra) algum = true;
                    });
                    nada.classList.toggle("d-none", algum);
                });
                card.insertBefore(filtro, ul);
                card.appendChild(nada);
                card.appendChild(botao);
            }
        }
        const rodape = el("div", "mt-auto pt-2");
        rodape.appendChild(botaoAgendar(a.id));
        card.appendChild(rodape);
        col.appendChild(card);
        return col;
    }

    // ---- quem paga mais
    async function carregarMateriais() {
        const ranking = $("cotRanking");
        const chips = $("cotChips");
        const sel = $("cotMaterial");
        chips.innerHTML = "";
        carregando(ranking);
        const meu = ++pedidoAtual;
        try {
            const dados = await buscarJson("/cotacoes/materiais", regiaoParams());
            if (meu !== pedidoAtual) return;
            const lista = dados.materiais || [];
            placeholder(sel, lista.length ? "Escolha um material..." : "Nenhum material com preço nesta região");
            sel.disabled = lista.length === 0;
            const porNome = lista.slice().sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
            porNome.forEach(m => opcao(sel, m.chave, m.nome + " (" + (m.unidade || "KG").toLowerCase() + ") – "
                + m.armazens + (m.armazens === 1 ? " armazém" : " armazéns")));
            lista.slice(0, MAX_CHIPS).forEach(m => {
                const b = el("button", "btn btn-sm btn-outline-success rounded-pill cot-chip", m.nome);
                b.type = "button";
                b.dataset.chave = m.chave;
                b.addEventListener("click", () => { sel.value = m.chave; escolherMaterial(m.chave); });
                chips.appendChild(b);
            });
            chips.parentElement.querySelector("p").classList.toggle("d-none", lista.length === 0);

            const salvo = estado.material || ler().material;
            if (salvo && lista.some(m => m.chave === salvo)) {
                sel.value = salvo;
                escolherMaterial(salvo);
            } else {
                estado.material = "";
                ranking.innerHTML = "";
                ranking.appendChild(el("p", "text-center text-muted py-3 mb-0",
                    lista.length ? "Escolha um material para ver quem paga mais por ele nesta região." : "Os armazéns desta região ainda não cadastraram preços."));
            }
        } catch (e) {
            if (meu !== pedidoAtual) return;
            falha(ranking, e.message, carregarMateriais);
        }
    }

    function marcarChip(chave) {
        document.querySelectorAll("#cotChips .cot-chip").forEach(b => {
            const ativo = b.dataset.chave === chave;
            b.classList.toggle("btn-success", ativo);
            b.classList.toggle("text-white", ativo);
            b.classList.toggle("btn-outline-success", !ativo);
        });
    }

    async function escolherMaterial(chave) {
        estado.material = chave;
        gravar();
        marcarChip(chave);
        const ranking = $("cotRanking");
        carregando(ranking);
        const meu = ++pedidoAtual;
        try {
            const dados = await buscarJson("/cotacoes/ranking", Object.assign(regiaoParams(), { material: chave }));
            if (meu !== pedidoAtual) return;
            ranking.innerHTML = "";
            const itens = dados.itens || [];
            if (itens.length === 0) {
                ranking.appendChild(el("p", "text-center text-muted py-3 mb-0", "Nenhum armazém desta região tem esse material agora."));
                return;
            }
            const un = (dados.material.unidade || "KG").toLowerCase();
            ranking.appendChild(el("p", "small text-muted text-center mb-2",
                "Quem paga mais por " + (dados.material.nome || "este material") + " (" + un + ")"));
            const ol = el("ol", "list-unstyled mb-0");
            itens.forEach(it => {
                const li = el("li", "cot-rank d-flex align-items-center gap-3" + (it.melhor ? " cot-rank-melhor" : ""));
                li.appendChild(el("span", "cot-pos", it.posicao + "º"));
                const meio = el("div", "flex-grow-1 min-w-0");
                const linhaNome = el("div", "d-flex align-items-center flex-wrap gap-2");
                linhaNome.appendChild(el("span", "cot-rank-nome notranslate", it.nome));
                if (it.melhor) {
                    const b = el("span", "badge rounded-pill bg-warning text-dark");
                    b.appendChild(el("i", "bi bi-trophy-fill me-1"));
                    b.appendChild(document.createTextNode("Paga mais"));
                    linhaNome.appendChild(b);
                }
                meio.appendChild(linhaNome);
                const selo = seloDias(it.dias, "Preço atualizado ");
                if (selo) meio.appendChild(selo);
                const preco = el("div", "cot-rank-preco notranslate text-end");
                preco.appendChild(document.createTextNode(dinheiro(it.preco)));
                preco.appendChild(el("small", "d-block text-muted fw-normal", "por " + (it.unidade || "KG").toLowerCase()));
                li.appendChild(meio);
                li.appendChild(preco);
                const acao = el("div", "flex-shrink-0");
                acao.appendChild(botaoAgendar(it.id, "btn btn-sm btn-outline-success rounded-pill"));
                li.appendChild(acao);
                ol.appendChild(li);
            });
            ranking.appendChild(ol);
        } catch (e) {
            if (meu !== pedidoAtual) return;
            falha(ranking, e.message, () => escolherMaterial(chave));
        }
    }

    // ------------------------------------------------------------ início
    async function iniciar() {
        const selUf = $("cotUf");
        if (!selUf) return;
        selUf.disabled = true;

        selUf.addEventListener("change", aoMudarUf);
        $("cotCidade").addEventListener("change", aoMudarCidade);
        document.querySelectorAll("#cotVisoes [data-visao]").forEach(b =>
            b.addEventListener("click", () => { if (b.dataset.visao !== estado.visao) mostrarVisao(b.dataset.visao); }));
        $("cotBusca").addEventListener("input", () => {
            clearTimeout(esperaBusca);
            esperaBusca = setTimeout(() => {
                const t = $("cotBusca").value.trim();
                if (t === estado.busca) return;
                estado.busca = t;
                carregarArmazens(true);
            }, 300);
        });
        $("cotVerMais").addEventListener("click", () => { estado.pagina++; carregarArmazens(false); });
        $("cotMaterial").addEventListener("change", () => { if ($("cotMaterial").value) escolherMaterial($("cotMaterial").value); });

        try {
            regioes = await buscarJson("/cotacoes/regioes", {});
        } catch (e) {
            placeholder(selUf, "Indisponível");
            aviso("Não foi possível carregar os preços agora. Atualize a página em instantes.", "bi-wifi-off");
            return;
        }
        if (!regioes.total) {
            placeholder(selUf, "Nenhum armazém ainda");
            aviso("Ainda não há armazéns com preços publicados.", "bi-shop");
            return;
        }
        preencherUfs();

        const salvo = ler();
        if (salvo.visao) estado.visao = salvo.visao === "material" ? "material" : "armazem";
        if (salvo.material) estado.material = salvo.material;
        const opcoes = Array.from(selUf.options).map(o => o.value).filter(v => v);
        let uf = salvo.uf && opcoes.indexOf(salvo.uf) >= 0 ? salvo.uf : (opcoes.length === 1 ? opcoes[0] : "");
        if (!uf) return;

        selUf.value = uf;
        estado.uf = uf;
        const cidades = preencherCidades(uf);
        if (uf === SEM_REGIAO) { regiaoPronta(); return; }
        const cidade = cidades.find(c => c.chave === salvo.cidade) ? salvo.cidade : (cidades.length === 1 ? cidades[0].chave : "");
        if (!cidade) { aviso("Agora escolha a cidade.", "bi-buildings"); return; }
        $("cotCidade").value = cidade;
        estado.cidade = cidade;
        regiaoPronta();
    }

    document.addEventListener("DOMContentLoaded", iniciar);
})();
