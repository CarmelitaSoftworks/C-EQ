import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cl.ecualizador"
    compileSdk = 35
    defaultConfig {
        applicationId = "cl.ecualizador"
        minSdk = 26
        targetSdk = 35
        versionCode = 38
        versionName = "3.6"
        // Día del último commit (días desde 1970), para recordar buscar una versión nueva sin usar
        // Internet. Sale del commit y no del reloj: el mismo código da siempre el mismo APK
        buildConfigField("long", "BUILD_DAY", "${commitDay()}L")
    }
    // Firma de release: datos en keystore.properties (fuera de git). Sin ese archivo, el APK de
    // release sale sin firmar.
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keystoreProps != null) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            listOf("previewDir", "classicWszDir", "winampExeBitmaps", "windowsOut", "importSkin", "wallpaperPhoto", "promoDir", "originalWindowsOut").forEach { k ->
                it.systemProperty(k, System.getProperty(k) ?: "")
            }
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}

/** Día (desde 1970) del último commit, o 0 si no hay git. */
fun commitDay(): Long = runCatching {
    providers.exec {
        commandLine("git", "log", "-1", "--format=%ct")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toLong() / 86_400L
}.getOrDefault(0L)
