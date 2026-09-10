package net.fribbynetwork.iamhere.net

/**
 * Conteggio dei caratteri di un SMS.
 *
 * Un messaggio scritto con il solo alfabeto GSM entra in 160 caratteri;
 * basta pero una lettera fuori da quell'alfabeto, una virgoletta curva,
 * un trattino lungo, un emoji, perche il messaggio passi alla codifica
 * Unicode e il limite scenda a 70. I messaggi lunghi vengono spezzati, e
 * ogni pezzo perde qualche carattere per l'intestazione che li ricuce:
 * 153 invece di 160, 67 invece di 70.
 */
object Sms {

    private const val BASE =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
        "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"

    /** Questi esistono nell'alfabeto GSM ma occupano due caratteri. */
    private const val ESTESI = "^{}\\[~]|€"

    data class Conteggio(
        val caratteri: Int,
        val messaggi: Int,
        val unicode: Boolean,
        val limite: Int
    )

    fun conta(testo: String): Conteggio {
        val unicode = testo.any { it !in BASE && it !in ESTESI }

        /*
         * Un ciclo esplicito invece di sumOf: quest'ultimo ha due varianti,
         * una che restituisce Int e una Long, e il compilatore non sempre
         * riesce a scegliere.
         *
         * In UCS-2 ogni unita UTF-16 occupa un posto, quindi la lunghezza
         * della stringa e gia il conto giusto: un emoji, che di unita ne
         * usa due, conta per due.
         */
        var n = 0
        if (unicode) {
            n = testo.length
        } else {
            for (c in testo) {
                n += if (c in ESTESI) 2 else 1
            }
        }

        val singolo = if (unicode) 70 else 160
        val spezzato = if (unicode) 67 else 153

        val messaggi: Int = when {
            n == 0 -> 0
            n <= singolo -> 1
            else -> (n + spezzato - 1) / spezzato
        }

        return Conteggio(n, messaggi, unicode, if (messaggi > 1) spezzato else singolo)
    }
}
