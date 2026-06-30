package com.example.healthmanager.device

object Stm32JsonStreamExtractor {
    @JvmStatic
    fun extractJsonObjects(text: String): List<String> {
        val objects = mutableListOf<String>()
        var start = -1
        var depth = 0
        var inString = false
        var escaping = false

        text.forEachIndexed { index, char ->
            if (escaping) {
                escaping = false
                return@forEachIndexed
            }

            if (char == '\\' && inString) {
                escaping = true
                return@forEachIndexed
            }

            if (char == '"') {
                inString = !inString
                return@forEachIndexed
            }

            if (inString) return@forEachIndexed

            when (char) {
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }

                '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && start >= 0) {
                            objects += text.substring(start, index + 1)
                            start = -1
                        }
                    }
                }
            }
        }

        return objects
    }
}
