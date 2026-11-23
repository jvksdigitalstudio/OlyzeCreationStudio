package com.yeivikas.olyzecs.engine.audio.decode

import java.io.InputStream

/** Lee hasta llenar [buffer] o hasta el fin del flujo; devuelve cuántos bytes obtuvo. (`read` puede devolver menos de lo pedido.) */
internal fun readFully(input: InputStream, buffer: ByteArray): Int {
    var total = 0
    while (total < buffer.size) {
        val read = input.read(buffer, total, buffer.size - total)
        if (read < 0) break
        total += read
    }
    return total
}
