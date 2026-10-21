// ============================================================
//  MediCase Clínico - app/build.gradle.kts (nivel módulo)
// ============================================================
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.secrets.gradle)
}

if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// UUID del estuche que local.properties puede sobrescribir. Se pasan a BuildConfig a
// mano y no con el Secrets Gradle Plugin porque este escribe una clave vacía como
// `String X = ;`, que no compila: así una copia limpia, sin local.properties o con
// las claves en blanco, compila y GattSpec usa los UUID estándar de Bluetooth SIG.
val bleUuidOverrideKeys = listOf(
    "BLE_SERVICE_UUID",
    "BLE_TEMPERATURE_CHAR_UUID",
    "BLE_BATTERY_CHAR_UUID",
)

// Broker MQTT del estuche (ver hardware/mqtt-protocol.md). Mismo mecanismo que los UUID:
// todas opcionales, y MqttConfig las valida en tiempo de ejecución. Sin MQTT_HOST o
// sin MQTT_TOPIC_PREFIX la app usa Bluetooth. Sus valores reales viven solo en
// local.properties: nunca en un archivo versionado.
val mqttConfigKeys = listOf(
    "MQTT_HOST",
    "MQTT_PORT",
    "MQTT_USE_TLS",
    "MQTT_TOPIC_PREFIX",
    "MQTT_USERNAME",
    "MQTT_PASSWORD",
    "MQTT_CASE_ID",
)

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** Valor de [key] como literal Java entre comillas; cadena vacía si falta o está en blanco. */
fun optionalLocalProperty(key: String): String {
    val value = localProperties.getProperty(key).orEmpty().trim()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$value\""
}

android {
    namespace = "com.example.medicaseclinico"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.medicaseclinico"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-mvp"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        (bleUuidOverrideKeys + mqttConfigKeys).forEach { key ->
            buildConfigField("String", key, optionalLocalProperty(key))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Las reglas propias viven en src/main/keepRules/ (AGP las combina solas).
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Cada módulo de Netty (dependencia de HiveMQ) trae su propia copia de estos dos
        // archivos de metadatos; en el APK no se usan y duplicados impiden empaquetar.
        // El registro de BlockHound (detector para servidores) apunta a una clase que
        // no existe en Android.
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/services/reactor.blockhound.integration.BlockHoundIntegration"
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

room {
    schemaDirectory("$projectDir/schemas")
}

secrets {
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.properties.example"
    // Los UUID y el broker MQTT del estuche se declaran arriba; las claves FIREBASE_* no
    // se leen desde el código (Firebase se configura con google-services.json).
    // Ignorarlas evita que una clave vacía rompa la compilación.
    ignoreList.add("BLE_.*")
    ignoreList.add("MQTT_.*")
    ignoreList.add("FIREBASE_.*")
}

dependencies {
    // --- AndroidX Core & Activity ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // --- Preferencias del usuario (DataStore) ---
    implementation(libs.androidx.datastore.preferences)

    // --- Lifecycle / MVVM ---
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // --- Jetpack Compose + Material 3 ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // --- Navegación ---
    implementation(libs.androidx.navigation.compose)

    // --- Room (misma versión que el plugin androidx.room, vía Version Catalog) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Corrutinas ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // --- Mapa (MapLibre + OpenStreetMap, sin API key) ---
    implementation(libs.maplibre.android.sdk)

    // --- MQTT (broker del estuche; ver hardware/mqtt-protocol.md) ---
    implementation(libs.hivemq.mqtt.client)

    // --- Firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json de Android en los tests JVM es solo un esqueleto ("Stub!"): esta es la
    // implementación real, para probar el codec MQTT. No entra en el APK.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}