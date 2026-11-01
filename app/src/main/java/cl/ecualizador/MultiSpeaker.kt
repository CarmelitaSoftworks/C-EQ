package cl.ecualizador

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build

/**
 * Asistente «Varios parlantes». Android no deja que una app mande música a dos equipos Bluetooth ni
 * una parlantes de distintas marcas (eso lo hacen el sistema o el firmware de cada parlante), así que
 * aquí solo se ve qué camino funciona con este teléfono y estos parlantes, y se lleva al ajuste o a
 * la app que corresponde. El ecualizador sigue sonando en todos, porque actúa antes de que el sonido
 * salga del teléfono.
 */
object MultiSpeaker {
    /** Marca de parlante con su modo para unir varios (lo hace el parlante, desde la app de la marca). */
    data class Brand(val name: String, val mode: String, val app: String, val pattern: Regex)

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    val BRANDS = listOf(
        Brand("Sony", "Party Connect", "Sony | Music Center", r("""\bsony\b|srs-|\bult[ -]|\bgtk-|\bmhc-""")),
        Brand("JBL", "PartyBoost / Auracast", "JBL Portable", r("""\bjbl\b|\bflip ?\d|\bcharge ?\d|partybox|boombox|\bxtreme|\bpulse ?\d|\bclip ?\d""")),
        Brand("Ultimate Ears", "PartyUp", "UE | BOOM", r("""ultimate ears|wonderboom|megaboom|everboom|hyperboom|\bue boom|\bboom ?\d""")),
        Brand("Soundcore", "PartyCast", "Soundcore", r("""soundcore|\banker\b""")),
        Brand("Marshall", "Stack Mode", "Marshall Bluetooth", r("""marshall|emberton|stanmore|acton|woburn|kilburn|middleton|willen""")),
        Brand("Bose", "Party Mode", "Bose", r("""\bbose\b|soundlink""")),
        Brand("Tribit", "Party Mode", "Tribit", r("""tribit|stormbox|xsound""")),
    )

    fun brandOf(name: String): Brand? = BRANDS.firstOrNull { it.pattern.containsMatchIn(name) }

    /** Lo que se puede hacer, en el orden en que conviene. */
    sealed interface Option {
        /** Dos o más parlantes de la misma marca: su propio modo, sincronizado y sin desfase. */
        data class SameBrand(val brand: Brand, val count: Int) : Option
        /** El teléfono transmite LE Audio (Auracast): funciona entre marcas compatibles. */
        data object Auracast : Option
        /** Samsung: el sonido a dos equipos Bluetooth de cualquier marca. */
        data object DualAudio : Option
        /** Un parlante con entrada AUX: siempre funciona. */
        data object Cable : Option
    }

    /**
     * Opciones para estos parlantes vinculados ([speakers], por nombre), si el teléfono transmite
     * LE Audio ([broadcast]) y si es un Samsung ([samsung]).
     */
    fun plan(speakers: List<String>, broadcast: Boolean, samsung: Boolean): List<Option> {
        val sameBrand = speakers.mapNotNull(::brandOf).groupingBy { it }.eachCount()
            .filter { it.value >= 2 }.entries.sortedByDescending { it.value }
            .map { Option.SameBrand(it.key, it.value) }
        return sameBrand + listOfNotNull(
            Option.Auracast.takeIf { broadcast },
            Option.DualAudio.takeIf { samsung },
            Option.Cable,
        )
    }

    fun isSamsung() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /** Si el teléfono puede transmitir LE Audio (Android 13+ lo informa). */
    fun canBroadcast(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return runCatching { adapter.isLeAudioBroadcastSourceSupported == BluetoothStatusCodes.FEATURE_SUPPORTED }.getOrDefault(false)
    }

    fun hasPermission(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Parlantes vinculados (no audífonos ni autos), por nombre; vacío sin permiso o sin Bluetooth. */
    @SuppressLint("MissingPermission")
    fun pairedSpeakers(ctx: Context): List<String> {
        if (!hasPermission(ctx)) return emptyList()
        val bonded = runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices }.getOrNull().orEmpty()
        return bonded.mapNotNull { d ->
            val name = runCatching { d.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val kind = AutoProfile.classifyBluetooth(name, runCatching { d.bluetoothClass?.deviceClass }.getOrNull())
            name.takeIf { kind == OutputKind.BT_PORTABLE_SPEAKER || kind == OutputKind.BT_PARTY_SPEAKER || brandOf(name) != null && kind == OutputKind.BT_UNKNOWN }
        }.sorted()
    }
}
