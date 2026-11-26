package com.opervia.example.serviceCompliance.features.operations

import java.util.Base64

/** PNG RGB de 1x1 usado exclusivamente como dato de pruebas, nunca como evidencia del usuario. */
internal object PhotoTestData {
    val png: ByteArray get() = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGOQ6DgDAAIoAW2w0NvjAAAAAElFTkSuQmCC")
}
