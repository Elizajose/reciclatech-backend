// Fase 32: chamados. Tema, conversa rolada até a última mensagem, trava contra duplo envio.
document.addEventListener('DOMContentLoaded', () => {
    try { document.documentElement.setAttribute('data-bs-theme', localStorage.getItem('theme') || 'light'); } catch (e) { /* sem storage */ }
    const conversa = document.querySelector('.conversa');
    if (conversa) conversa.scrollTop = conversa.scrollHeight;
    document.querySelectorAll('form').forEach(f => f.addEventListener('submit', () => {
        setTimeout(() => f.querySelectorAll('button[type=submit]').forEach(b => { b.disabled = true; }), 0);
    }));
});

// Fase 34: mostra o nome do arquivo escolhido e recusa acima de 5 MB antes de enviar
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.anexo-escolher input[type=file]').forEach(inp => {
        const rotulo = inp.parentElement.querySelector('.anexo-nome');
        const original = rotulo ? rotulo.textContent : '';
        inp.addEventListener('change', () => {
            const f = inp.files && inp.files[0];
            if (f && f.size > 5 * 1024 * 1024) { alert('O anexo passa de 5 MB.'); inp.value = ''; }
            const ok = inp.files && inp.files[0];
            if (rotulo) rotulo.textContent = ok ? ok.name : original;
            inp.parentElement.classList.toggle('tem-arquivo', !!ok);
        });
    });
});
