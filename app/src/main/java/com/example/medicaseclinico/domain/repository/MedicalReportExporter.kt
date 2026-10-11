package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.MedicalReport

/**
 * Convierte un [MedicalReport] en un documento que se puede compartir.
 *
 * Es una interfaz del dominio para que el ViewModel no conozca el formato
 * (PDF) ni dónde se guarda el archivo, y para poder sustituirla por una
 * implementación falsa en los tests.
 */
interface MedicalReportExporter {
    /**
     * @return Success con la URI del documento generado, lista para
     * compartirse con otra app; Failure si no se pudo escribir el archivo.
     */
    suspend fun export(report: MedicalReport): Result<String>
}
