// Fase 32: chamados. Tema, conversa rolada até a última mensagem, trava contra duplo envio.
document.addEventListener('DOMContentLoaded', () => {
    try { document.documentElement.setAttribute('data-bs-theme', localStorage.getItem('theme') || 'light'); } catch (e) { /* sem storage */ }
    const conversa = document.querySelector('.conversa');
    if (conversa) conversa.scrollTop = conversa.scrollHeight;
    document.querySelectorAll('form').forEach(f => f.addEventListener('submit', () => {
        setTimeout(() => f.querySelectorAll('button[type=submit]').forEach(b => { b.disabled = true; }), 0);
    }));
});
