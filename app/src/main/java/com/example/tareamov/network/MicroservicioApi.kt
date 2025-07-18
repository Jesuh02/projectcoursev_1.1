package com.example.tareamov.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Call

// Data class para la petición
data class MicroservicioPromptRequest(
    val prompt: String,
    val ollamaUrl: String = "http://localhost:11434",
    val model: String = "granite-code"
)

// Data class para la respuesta
data class MicroservicioPromptResponse(
    val respuesta_texto: String?,
    val aviso: String? = null,
    val error: String? = null,
    val detalle: String? = null
)

interface MicroservicioApi {
    @POST("/procesar-prompt")
    fun procesarPrompt(@Body request: MicroservicioPromptRequest): Call<MicroservicioPromptResponse>
}
