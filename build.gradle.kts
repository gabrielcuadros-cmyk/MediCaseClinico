// ============================================================
//  MediCase Clínico - build.gradle.kts (nivel proyecto)
//  Declara los plugins disponibles para los módulos.
// ============================================================
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // Persistencia: Room con procesamiento KSP
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false

    // Google Maps: Secrets Gradle Plugin
    alias(libs.plugins.secrets.gradle) apply false

    // Firebase: Google Services
    alias(libs.plugins.google.services) apply false
}