package cl.ecualizador

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build

/** Lo que se sabe de la salida de audio actual. */
data class OutputInfo(val key: String, val name: String, val kind: OutputKind, val details: List<String>)

/** Analiza la salida activa: tipo, nombre, clase Bluetooth y formatos que acepta. */
object DeviceAnalyzer {
    private val WIRED = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
    private val USB = setOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)
    // Bluetooth LE Audio (Android 12+): audífonos y parlantes nuevos, p. ej. Galaxy Buds2 Pro
    private const val BLE_HEADSET = 26
    private const val BLE_SPEAKER = 27
    private const val BLE_BROADCAST = 30
    val HDMI = setOf(AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, 29 /* HDMI eARC */)

    /** Salidas Bluetooth: la clásica (A2DP) y LE Audio. */
    val BLUETOOTH = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, BLE_HEADSET, BLE_SPEAKER, BLE_BROADCAST)

    fun analyze(ctx: Context): OutputInfo {
        val outs = ctx.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outs.firstOrNull { it.type in BLUETOOTH }
        if (bt != null) {
            val name = bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
            val btClass = bluetoothClass(ctx, bt, name)
            // LE Audio dice si es audífono o parlante aunque el nombre no se reconozca
            val kind = AutoProfile.classifyBluetooth(name, btClass).let { k ->
                when {
                    k != OutputKind.BT_UNKNOWN -> k
                    bt.type == BLE_HEADSET -> OutputKind.BT_HEADPHONES
                    bt.type == BLE_SPEAKER -> OutputKind.BT_PORTABLE_SPEAKER
                    else -> k
                }
            }
            val details = formatDetails(bt) + listOfNotNull(
                btClass?.let { "Clase Bluetooth: ${classLabel(it)} (0x%04X)".format(it) }
                    ?: if (!hasBtPermission(ctx)) "Clase Bluetooth: sin permiso para leerla" else null,
                if (kind == OutputKind.BT_UNKNOWN) "Tipo no reconocido por el nombre" else "Reconocido como: ${ctx.getString(kind.label)}",
                HeadphoneProfiles.match(name)?.let { "Curva medida de AutoEQ: ${it.model}" },
            )
            return OutputInfo("bt:$name", name, kind, details)
        }
        outs.firstOrNull { it.type in WIRED }?.let {
            return OutputInfo("wired", ctx.getString(R.string.kind_wired), OutputKind.WIRED_HEADPHONES, formatDetails(it))
        }
        outs.firstOrNull { it.type in USB }?.let {
            val name = it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: ctx.getString(R.string.kind_usb)
            return OutputInfo("wired", name, OutputKind.USB_AUDIO, formatDetails(it))
        }
        outs.firstOrNull { it.type in HDMI }?.let {
            val name = it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: ctx.getString(R.string.out_hdmi)
            return OutputInfo("hdmi", name, OutputKind.BT_TV_SOUNDBAR, formatDetails(it) + "Conectado por HDMI")
        }
        val speaker = outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        return OutputInfo("speaker", ctx.getString(R.string.kind_phone_speaker), OutputKind.PHONE_SPEAKER, speaker?.let { formatDetails(it) }.orEmpty())
    }

    fun hasBtPermission(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun bluetoothClass(ctx: Context, dev: AudioDeviceInfo, name: String): Int? {
        if (!hasBtPermission(ctx)) return null
        val bonded = runCatching {
            ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
        }.getOrNull().orEmpty()
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dev.address else null
        val match = bonded.firstOrNull { address != null && it.address.equals(address, ignoreCase = true) }
            ?: bonded.firstOrNull { runCatching { it.name }.getOrNull() == name }
        return match?.bluetoothClass?.deviceClass
    }

    private fun classLabel(c: Int) = when (c) {
        0x0404, 0x0408 -> "manos libres / headset"
        0x0414 -> "parlante"
        0x0418 -> "audífonos"
        0x041C -> "audio portátil"
        0x0420 -> "audio de auto"
        0x0428 -> "equipo Hi-Fi"
        0x043C -> "pantalla con parlantes"
        else -> "audio"
    }

    private fun formatDetails(d: AudioDeviceInfo): List<String> {
        val rates = d.sampleRates.toList().sorted()
        val channels = d.channelCounts.toList()
        val encodings = d.encodings.toList().mapNotNull {
            when (it) {
                AudioFormat.ENCODING_PCM_16BIT -> "16 bits"
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24 bits"
                AudioFormat.ENCODING_PCM_32BIT -> "32 bits"
                AudioFormat.ENCODING_PCM_FLOAT -> "coma flotante"
                else -> null
            }
        }.distinct()
        return listOfNotNull(
            "Frecuencias de muestreo: " + (
                if (rates.isEmpty()) "las que pida el sistema"
                else rates.joinToString(" / ") { r: Int -> if (r % 1000 == 0) "${r / 1000} kHz" else "%.1f kHz".format(r / 1000f) }
                ),
            "Canales: " + when {
                channels.isEmpty() -> "según el contenido"
                channels.max() >= 2 -> "estéreo"
                else -> "mono"
            },
            encodings.takeIf { it.isNotEmpty() }?.let { "Formatos: " + it.joinToString(", ") },
        )
    }
}
