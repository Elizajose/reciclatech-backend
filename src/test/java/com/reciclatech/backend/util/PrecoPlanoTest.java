package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrecoPlanoTest {

    @Test void startEProSaoOsValoresDaTabela() {
        assertEquals(149.90, PrecoPlano.individual("START"), 0.001);
        assertEquals(199.90, PrecoPlano.individual("PRO"), 0.001);
        assertEquals(149.90, PrecoPlano.individual(null), 0.001);
    }

    @Test void personalizadoComUmArmazemCustaComoOPro() {
        assertEquals(199.90, PrecoPlano.personalizado(1, 129.90), 0.001);
        assertEquals(199.90, PrecoPlano.personalizado(0, 129.90), 0.001);
    }

    @Test void cadaArmazemAMaisSomaOValorExtra() {
        assertEquals(329.80, PrecoPlano.personalizado(2, 129.90), 0.001);
        assertEquals(459.70, PrecoPlano.personalizado(3, 129.90), 0.001);
        assertEquals(849.40, PrecoPlano.personalizado(6, 129.90), 0.001);
    }

    @Test void extraInvalidoUsaOPadrao() {
        assertEquals(329.80, PrecoPlano.personalizado(2, 0), 0.001);
        assertEquals(329.80, PrecoPlano.personalizado(2, -5), 0.001);
    }

    @Test void tresArmazensNoPersonalizadoSaemMaisBaratosQueTresProsMasMaisCarosQueUm() {
        double tresPro = 3 * PrecoPlano.PRO;
        double personalizado = PrecoPlano.personalizado(3, PrecoPlano.EXTRA_PADRAO);
        assertTrue(personalizado < tresPro);
        assertTrue(personalizado > PrecoPlano.PRO);
    }
}
