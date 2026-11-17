/*
 * Foto do dono e logo dos armazéns (Fase 25).
 * O navegador reduz a imagem antes de enviar (celular tira foto de vários MB); o servidor confere e regrava de novo.
 */
(function () {
    var entrada = document.createElement('input');
    entrada.type = 'file';
    entrada.accept = 'image/*';
    entrada.hidden = true;
    document.body.appendChild(entrada);
    var destino = null;

    function reduzir(arquivo, ladoMax) {
        return new Promise(function (ok, erro) {
            var url = URL.createObjectURL(arquivo);
            var img = new Image();
            img.onload = function () {
                var r = Math.min(1, ladoMax / Math.max(img.width, img.height));
                var c = document.createElement('canvas');
                c.width = Math.max(1, Math.round(img.width * r));
                c.height = Math.max(1, Math.round(img.height * r));
                var x = c.getContext('2d');
                x.fillStyle = '#fff';
                x.fillRect(0, 0, c.width, c.height);
                x.drawImage(img, 0, 0, c.width, c.height);
                URL.revokeObjectURL(url);
                c.toBlob(function (b) { if (b) ok(b); else erro(); }, 'image/jpeg', 0.88);
            };
            img.onerror = function () { URL.revokeObjectURL(url); erro(); };
            img.src = url;
        });
    }

    document.addEventListener('click', function (ev) {
        var botao = ev.target.closest('[data-enviar-imagem]');
        if (!botao) return;
        destino = botao.getAttribute('data-enviar-imagem');
        entrada.value = '';
        entrada.click();
    });

    entrada.addEventListener('change', function () {
        var arquivo = entrada.files && entrada.files[0];
        if (!arquivo || !destino) return;
        var alvo = destino;
        reduzir(arquivo, 640).then(function (blob) {
            var dados = new FormData();
            dados.append('arquivo', blob, 'imagem.jpg');
            return fetch(alvo, { method: 'POST', body: dados, credentials: 'same-origin' });
        }).then(function (resp) {
            return resp.text().then(function (t) { return { ok: resp.ok, texto: t }; });
        }).then(function (r) {
            if (r.ok) window.location.reload();
            else alert(r.texto || 'Não foi possível enviar a imagem.');
        }).catch(function () {
            alert('Não foi possível usar essa imagem. Tente uma foto em JPG ou PNG.');
        });
    });
})();
