package info.lwb.testfixtures

import java.nio.charset.StandardCharsets

object JsonResource {
    fun fromClasspath(path: String): String {
        val stream = requireNotNull(javaClass.getResourceAsStream(path)) {
            "Resource not found: $path"
        }
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
}
