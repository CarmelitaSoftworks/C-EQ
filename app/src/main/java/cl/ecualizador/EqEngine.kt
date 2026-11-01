package cl.ecualizador

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class EqUiState(
    val enabled: Boolean = false,
    val engine: String = "",
    val error: String? = null,
    val hasControl: Boolean = true,
    val profileKey: String = "speaker",
    val profileName: String = "",
    val levels: List<Float> = List(EqCore.BANDS) { 0f },
    val bass: Float = 0f,
    val boost: Float = 0f,
    val balance: Float = 0f,
    /** DynamicsProcessing activo: permite refuerzo de volumen y balance. */
    val advanced: Boolean = false,
    val preset: String? = "Plano",
    val headroom: EqCore.Headroom = EqCore.Headroom.BALANCED,
    /** Compensación de volumen bajo (loudness). */
    val loudness: Boolean = false,
    val canUndo: Boolean = false,
    val autoReclaim: Boolean = true,
    val customPresets: List<String> = emptyList(),
    /** Ajuste automático sin micrófono ([AutoEq]): la curva sigue al equipo, lo que suena y el volumen. */
    val auto: Boolean = false,
    /** Curva automática que suena ahora y por qué (solo con [auto]). */
    val autoLevels: List<Float>? = null,
    val autoWhy: String = "",
    val autoContent: ListenContent = ListenContent.MUSIC,
    /** Protección del altavoz del teléfono (ver [EqCore.protectSpeaker]). */
    val speakerProtect: Boolean = true,
    /** Sonido envolvente (Virtualizer de Android), 0..1, si el teléfono lo permite en todo el audio. */
    val surround: Float = 0f,
    val surroundSupported: Boolean = false,
) {
    /** La curva que se ve: la automática si está activa, si no la del perfil. */
    val shownLevels: List<Float> get() = autoLevels ?: levels
}

/**
 * Ecualizador global de 10 bandas sobre la sesión de audio 0 (toda la salida del teléfono):
 *  - DynamicsProcessing (Android 9+): preEQ de 10 bandas + limitador para no saturar.
 *  - Si el teléfono no lo permite: android.media.audiofx.Equalizer con la curva interpolada.
 *  - Perfil distinto para cada salida (equipo Bluetooth por nombre, audífonos, altavoz).
 *  - Si otra app toma la sesión 0, intenta recuperarla (opcional).
 */
class EqEngine private constructor(private val ctx: Context) {
    companion object {
        private const val PRIORITY = Int.MAX_VALUE
        private const val RAMP_STEPS = 8
        private const val RAMP_STEP_MS = 20L
        @Volatile private var instance: EqEngine? = null
        fun get(ctx: Context): EqEngine =
            instance ?: synchronized(this) { instance ?: EqEngine(ctx.applicationContext).also { instance = it } }

        /** El motor si ya se creó (sin crearlo). */
        fun peek(): EqEngine? = instance
    }

    val state = MutableStateFlow(EqUiState())
    private val prefs = ctx.getSharedPreferences("eq2", Context.MODE_PRIVATE)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var dp: DynamicsProcessing? = null
    private var legacy: Equalizer? = null
    private var virtualizer: android.media.audiofx.Virtualizer? = null
    private var reclaimAttempts = 0
    /** Durante la calibración el efecto queda plano para medir el equipo sin ecualizar. */
    @Volatile private var bypass = false


    // ---------- Perfiles por salida ----------

    private fun currentOutput(): Pair<String, String> {
        val outs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        outs.firstOrNull { it.type in DeviceAnalyzer.BLUETOOTH }?.let {
            val name = it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: "Bluetooth"
            return "bt:$name" to name
        }
        val wired = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
        )
        if (outs.any { it.type in wired }) return "wired" to ctx.getString(R.string.out_wired_usb)
        if (outs.any { it.type in DeviceAnalyzer.HDMI }) return "hdmi" to ctx.getString(R.string.out_hdmi)
        return "speaker" to ctx.getString(R.string.kind_phone_speaker)
    }

    private fun onOutputChanged() {
        val out = currentOutput()
        if (out.first != state.value.profileKey) {
            switchProfile(out)
            apply(smooth = true)
            if (state.value.enabled) EqService.start(ctx)
        }
    }

    /** Tipo del equipo conectado, para el ajuste automático. */
    private var outputKind = OutputKind.PHONE_SPEAKER
    /** Curva medida del modelo de audífonos conectado, si se reconoce por su nombre Bluetooth. */
    private var outputProfile: HeadphoneProfiles.Profile? = null

    private fun switchProfile(out: Pair<String, String>) {
        outputKind = runCatching { DeviceAnalyzer.analyze(ctx).kind }.getOrDefault(OutputKind.PHONE_SPEAKER)
        outputProfile = out.second.takeIf { out.first.startsWith("bt:") }?.let(HeadphoneProfiles::match)
        // El historial de deshacer pertenece al perfil anterior: no debe aplicarse a otra salida.
        history.clear()
        val json = prefs.getString("profile_${out.first}", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val levels = json?.optJSONArray("levels")?.let { a -> List(EqCore.BANDS) { a.optDouble(it, 0.0).toFloat() } }
            ?: List(EqCore.BANDS) { 0f }
        state.update {
            it.copy(
                profileKey = out.first, profileName = out.second, levels = levels,
                bass = json?.optDouble("bass", 0.0)?.toFloat() ?: 0f,
                boost = json?.optDouble("boost", 0.0)?.toFloat() ?: 0f,
                balance = json?.optDouble("balance", 0.0)?.toFloat() ?: 0f,
                preset = json?.optString("preset")?.takeIf { p -> p.isNotEmpty() } ?: if (json == null) "Plano" else null,
                canUndo = false,
            )
        }
    }

    private fun saveProfile() {
        val s = state.value
        val json = JSONObject()
            .put("levels", JSONArray(s.levels.map { it.toDouble() }))
            .put("bass", s.bass.toDouble())
            .put("boost", s.boost.toDouble())
            .put("balance", s.balance.toDouble())
            .put("preset", s.preset ?: "")
        prefs.edit().putString("profile_${s.profileKey}", json.toString()).apply()
    }

    // ---------- Efecto de audio ----------

    private fun createEffect() {
        release()
        createSurround()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && createDynamics()) return
        createLegacy()
    }

    /**
     * Sonido envolvente sobre la mezcla global. Android lo desaconseja en la sesión 0 y muchos
     * fabricantes no lo permiten: si no se puede crear o no tiene intensidad ajustable, no se ofrece.
     */
    @Suppress("DEPRECATION")
    private fun createSurround() {
        val v = runCatching { android.media.audiofx.Virtualizer(PRIORITY - 1, 0) }.getOrNull()
        val ok = v != null && runCatching { v.strengthSupported }.getOrDefault(false)
        if (!ok) runCatching { v?.release() }
        virtualizer = v.takeIf { ok }
        if (ok) state.update { it.copy(surroundSupported = true) }
        applySurround()
    }

    /**
     * Si el teléfono permite el sonido envolvente en todo el audio, se averigua una vez al empezar
     * (creándolo apagado y soltándolo), para ofrecerlo aunque el ecualizador esté apagado.
     */
    @Suppress("DEPRECATION")
    private fun probeSurround(): Boolean {
        val v = runCatching { android.media.audiofx.Virtualizer(PRIORITY - 1, 0) }.getOrNull() ?: return false
        return runCatching { v.strengthSupported }.getOrDefault(false).also { runCatching { v.release() } }
    }

    private fun applySurround() {
        val v = virtualizer ?: return
        val s = state.value.surround
        runCatching {
            v.setStrength((s * 1000).toInt().coerceIn(0, 1000).toShort())
            v.enabled = s > 0.01f && !bypass
        }
    }

    fun setSurround(value: Float) {
        prefs.edit().putFloat("surround", value.coerceIn(0f, 1f)).apply()
        state.update { it.copy(surround = value.coerceIn(0f, 1f)) }
        applySurround()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createDynamics(): Boolean = try {
        val n = EqCore.BANDS
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
            true, n, false, 0, false, 0, true,
        ).build()
        val effect = DynamicsProcessing(PRIORITY, 0, config)
        effect.setControlStatusListener { _, granted -> main.post { onControl(granted) } }
        dp = effect
        state.update {
            it.copy(engine = "DynamicsProcessing · 10 bandas + limitador", error = null, hasControl = effect.hasControl(), advanced = true)
        }
        apply()
        effect.enabled = true
        true
    } catch (t: Throwable) {
        dp = null
        false
    }

    private fun createLegacy() {
        try {
            val e = Equalizer(PRIORITY, 0)
            e.setControlStatusListener { _, granted -> main.post { onControl(granted) } }
            legacy = e
            state.update {
                it.copy(engine = "Ecualizador básico · ${e.numberOfBands} bandas", error = null, hasControl = e.hasControl(), advanced = false)
            }
            apply()
            e.enabled = true
        } catch (t: Throwable) {
            legacy = null
            state.update {
                it.copy(engine = "", error = "Este teléfono no permite un ecualizador global (${t.message ?: t.javaClass.simpleName}).")
            }
        }
    }

    private fun onControl(granted: Boolean) {
        state.update { it.copy(hasControl = granted) }
        if (granted) {
            reclaimAttempts = 0
            apply()
        } else if (state.value.enabled && state.value.autoReclaim && reclaimAttempts < 5) {
            // Otra app tomó la sesión 0: reintentar con espera creciente (puede causar un corte breve).
            reclaimAttempts++
            main.postDelayed({ if (state.value.enabled && !state.value.hasControl) createEffect() }, 3000L * reclaimAttempts)
        }
    }

    /** Últimas ganancias enviadas al efecto: punto de partida de las transiciones suaves. */
    private var appliedGains = List(EqCore.BANDS) { 0f }
    private var rampStep = 0
    private val rampRunnable = object : Runnable {
        var from = appliedGains
        override fun run() {
            rampStep++
            val t = rampStep / RAMP_STEPS.toFloat()
            push(EqCore.ramp(from, targetGains(), t))
            if (rampStep < RAMP_STEPS) main.postDelayed(this, RAMP_STEP_MS)
        }
    }

    private fun musicVolume(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC) /
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat()

    /** Curva automática para lo que suena ahora (ver [AutoEq]). */
    private fun autoCurve(): Pair<AutoCurve, ListenContent> {
        val t = NowPlaying.track.value
        val content = if (t == null) ListenContent.MUSIC else AutoEq.contentOf(t.app, t.genre)
        return AutoEq.curve(outputKind, content, musicVolume(), outputProfile) to content
    }

    /**
     * Curva que debe sonar ahora: la automática o la elegida, los graves y la compensación de
     * volumen bajo (la automática ya la trae).
     */
    private fun targetGains(): List<Float> {
        val s = state.value
        if (bypass) return List(EqCore.BANDS) { 0f }
        if (s.auto) {
            val (curve, content) = autoCurve()
            if (s.autoLevels != curve.levels || s.autoWhy != curve.why || s.autoContent != content) {
                state.update { it.copy(autoLevels = curve.levels, autoWhy = curve.why, autoContent = content) }
            }
            return EqCore.finalGains(curve.levels, maxOf(curve.bass, s.bass))
        }
        val base = EqCore.finalGains(s.levels, s.bass)
        if (!s.loudness) return base
        return base.zip(EqCore.loudness(musicVolume())) { g, l -> g + l }
    }

    /**
     * Envía la curva actual al efecto. Con [smooth] la lleva en ~160 ms desde la anterior, para que
     * un cambio de preajuste no suene como un salto o un clic.
     */
    private fun apply(smooth: Boolean = false) {
        main.removeCallbacks(rampRunnable)
        if (!smooth) {
            push(targetGains())
            return
        }
        rampRunnable.from = appliedGains
        rampStep = 0
        main.post(rampRunnable)
    }

    private fun push(target: List<Float>) {
        appliedGains = target
        // En la comparación A/B (bypass) todo queda neutro: sin preamplificador, refuerzo ni limitador
        val raw = if (bypass) state.value.copy(boost = 0f, balance = 0f, headroom = EqCore.Headroom.LOUD) else state.value
        // Altavoz del teléfono: sin graves que no puede dar, poco volumen extra y sin margen libre
        val speaker = !bypass && raw.speakerProtect && raw.profileKey == "speaker"
        val s = if (speaker) raw.copy(boost = minOf(raw.boost, EqCore.SPEAKER_BOOST_MAX_DB), headroom = EqCore.Headroom.FULL) else raw
        val gains = if (speaker) EqCore.protectSpeaker(target) else target
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching {
            dp?.let { d ->
                val cut = EqCore.cutoffs()
                val eq = DynamicsProcessing.Eq(true, true, EqCore.BANDS)
                for (i in 0 until EqCore.BANDS) eq.setBand(i, DynamicsProcessing.EqBand(true, cut[i], gains[i]))
                d.setPreEqAllChannelsTo(eq)
                val (left, right) = EqCore.channelGains(EqCore.preamp(gains, s.headroom, hasLimiter = true), s.boost, s.balance)
                d.setInputGainbyChannel(0, left)
                d.setInputGainbyChannel(1, right)
                // El limitador es lo que evita la saturación cuando no se baja todo el volumen: siempre
                // activo, salvo en la comparación A/B.
                d.setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(
                        true, !bypass, 0, EqCore.LIMITER_ATTACK_MS, EqCore.LIMITER_RELEASE_MS,
                        EqCore.LIMITER_RATIO, EqCore.LIMITER_THRESHOLD_DB, 0f,
                    )
                )
            }
        }
        runCatching {
            legacy?.let { e ->
                val range = e.bandLevelRange
                // Sin preamplificador ni limitador: para evitar distorsión se baja toda la curva.
                val shift = EqCore.preamp(gains, s.headroom, hasLimiter = false)
                for (b in 0 until e.numberOfBands) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000f
                    val mb = ((EqCore.interpolate(gains, hz) + shift) * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                    e.setBandLevel(b.toShort(), mb.toShort())
                }
            }
        }
    }

    // ---------- Volumen (para la compensación de volumen bajo) ----------

    private val volumeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            if (state.value.enabled) apply(smooth = true)
        }
    }

    private var watchingVolume = false

    /** Sigue el volumen mientras lo necesiten la compensación de volumen bajo o el ajuste automático. */
    private fun watchVolume() {
        val on = state.value.loudness || state.value.auto
        if (on == watchingVolume) return
        watchingVolume = on
        if (on) {
            androidx.core.content.ContextCompat.registerReceiver(
                ctx, volumeReceiver, android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } else {
            runCatching { ctx.unregisterReceiver(volumeReceiver) }
        }
    }

    // ---------- Deshacer ----------

    private val history = ArrayDeque<Pair<List<Float>, Float>>()
    private var lastSnapshot = 0L

    /** Guarda el estado antes de un cambio; los arrastres seguidos cuentan como un solo cambio. */
    private fun snapshot(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (force || now - lastSnapshot > 800) {
            val s = state.value
            if (history.lastOrNull() != (s.levels to s.bass)) history.addLast(s.levels to s.bass)
            while (history.size > 30) history.removeFirst()
        }
        lastSnapshot = now
        state.update { it.copy(canUndo = history.isNotEmpty()) }
    }

    fun undo() {
        val (levels, bass) = history.removeLastOrNull() ?: return
        lastSnapshot = 0
        state.update { it.copy(levels = levels, bass = bass, preset = null, canUndo = history.isNotEmpty()) }
        apply(smooth = true); saveProfile()
    }

    private fun release() {
        runCatching { virtualizer?.release() }
        virtualizer = null
        runCatching { dp?.release() }
        runCatching { legacy?.release() }
        dp = null
        legacy = null
    }

    // ---------- Acciones de la interfaz ----------

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("enabled", on).apply()
        state.update { it.copy(enabled = on) }
        reclaimAttempts = 0
        if (on) createEffect() else release()
        if (on) EqService.start(ctx) else EqService.stop(ctx)
        EqTileService.refresh(ctx)
    }

    fun setBand(i: Int, db: Float) {
        leaveAuto()
        snapshot()
        state.update { s -> s.copy(levels = s.levels.toMutableList().also { it[i] = db.coerceIn(EqCore.MIN_DB, EqCore.MAX_DB) }, preset = null) }
        apply(); saveProfile()
    }

    fun setBass(v: Float) {
        snapshot()
        state.update { it.copy(bass = v.coerceIn(0f, 1f)) }
        apply(); saveProfile()
    }

    fun setBoost(db: Float) {
        state.update { it.copy(boost = db.coerceIn(0f, EqCore.BOOST_MAX_DB)) }
        apply(); saveProfile()
    }

    fun setBalance(v: Float) {
        state.update { it.copy(balance = v.coerceIn(-1f, 1f)) }
        apply(); saveProfile()
    }

    fun setBypass(on: Boolean) {
        bypass = on
        applySurround()
        apply()
    }

    /** Aplica una curva completa al perfil actual (perfil automático o calibrado). */
    fun applyCurve(levels: List<Float>, bass: Float, name: String) {
        leaveAuto(keepCurve = false)
        snapshot(force = true)
        state.update {
            it.copy(levels = levels.map { l -> l.coerceIn(EqCore.MIN_DB, EqCore.MAX_DB) }, bass = bass.coerceIn(0f, 1f), preset = name)
        }
        apply(smooth = true); saveProfile()
        if (!state.value.enabled) setEnabled(true)
    }

    /** Deja el perfil de la salida actual como recién instalado. */
    fun resetProfile() {
        leaveAuto(keepCurve = false)
        snapshot(force = true)
        state.update { it.copy(levels = EqCore.PRESETS.getValue("Plano"), bass = 0f, boost = 0f, balance = 0f, preset = "Plano") }
        apply(smooth = true); saveProfile()
    }

    fun usePreset(name: String) {
        val curve = EqCore.PRESETS[name] ?: customPresets()[name] ?: return
        leaveAuto(keepCurve = false)
        snapshot(force = true)
        state.update { it.copy(levels = curve, preset = name) }
        apply(smooth = true); saveProfile()
        if (!state.value.enabled) setEnabled(true)
    }

    fun setHeadroom(mode: EqCore.Headroom) {
        prefs.edit().putString("headroom", mode.name).apply()
        state.update { it.copy(headroom = mode) }
        apply(smooth = true)
    }

    fun setLoudness(on: Boolean) {
        prefs.edit().putBoolean("loudness", on).apply()
        state.update { it.copy(loudness = on) }
        watchVolume()
        apply(smooth = true)
    }

    /**
     * Ajuste automático sin micrófono. Al activarlo se enciende el ecualizador; al apagarlo vuelve la
     * curva del perfil.
     */
    fun setAuto(on: Boolean) {
        prefs.edit().putBoolean("auto", on).apply()
        state.update { it.copy(auto = on, autoLevels = null, autoWhy = "") }
        watchVolume()
        apply(smooth = true)
        if (on && !state.value.enabled) setEnabled(true)
    }

    /**
     * Mover una banda a mano deja el modo automático: con [keepCurve] la curva automática pasa al
     * perfil, para seguir desde lo que sonaba.
     */
    private fun leaveAuto(keepCurve: Boolean = true) {
        val s = state.value
        if (!s.auto) return
        prefs.edit().putBoolean("auto", false).apply()
        state.update {
            it.copy(auto = false, autoLevels = null, autoWhy = "", levels = if (keepCurve) s.autoLevels ?: s.levels else s.levels, preset = null)
        }
        watchVolume()
    }

    /** Cambió lo que suena: el ajuste automático puede cambiar (voz, video o música). */
    fun onMediaChanged() {
        if (state.value.auto && state.value.enabled) main.post { apply(smooth = true) }
    }

    /**
     * Deslizador de la ventana del ecualizador del skin: [band] de 0 a 9, o −1 para el
     * preamplificador (que maneja el volumen extra: de la mitad hacia arriba). [fraction] va de 0
     * (−12 dB) a 1 (+12 dB), en pasos de medio dB.
     */
    fun setFromSkin(band: Int, fraction: Float) {
        if (band < 0) {
            setBoost(((fraction - 0.5f) * 2f).coerceIn(0f, 1f) * EqCore.BOOST_MAX_DB)
            return
        }
        val db = Math.round((EqCore.MIN_DB + fraction.coerceIn(0f, 1f) * (EqCore.MAX_DB - EqCore.MIN_DB)) * 2) / 2f
        setBand(band, if (kotlin.math.abs(db) <= 0.5f) 0f else db)
        if (!state.value.enabled) setEnabled(true)
    }

    fun setSpeakerProtect(on: Boolean) {
        prefs.edit().putBoolean("speakerProtect", on).apply()
        state.update { it.copy(speakerProtect = on) }
        apply(smooth = true)
    }

    fun setAutoReclaim(on: Boolean) {
        prefs.edit().putBoolean("autoReclaim", on).apply()
        state.update { it.copy(autoReclaim = on) }
    }

    /** Pide recuperar la sesión 0 ahora mismo. */
    fun reclaimNow() {
        reclaimAttempts = 0
        if (state.value.enabled) createEffect()
    }

    private fun customPresets(): Map<String, List<Float>> {
        val json = runCatching { JSONObject(prefs.getString("customPresets", "{}")!!) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().associateWith { k ->
            val a = json.getJSONArray(k); List(EqCore.BANDS) { a.optDouble(it, 0.0).toFloat() }
        }
    }

    /** Nombres que la app usa para sus propias curvas: un preajuste propio no puede llamarse así. */
    private val reservedNames = EqCore.PRESETS.keys + setOf("Automático", "Calibrado", "Importado", "Personalizado")

    /**
     * Guarda preajustes propios en [json] y devuelve sus nombres finales. El nombre se recorta a 24
     * letras; si coincide con uno de la app (quedaría inaccesible) o, con [replace] en falso, con uno
     * propio, lleva un número: «Rock (2)».
     */
    private fun putCustom(json: JSONObject, presets: List<Pair<String, List<Float>>>, replace: Boolean): List<String> =
        presets.mapNotNull { (raw, levels) ->
            val base = raw.trim().take(24)
            if (base.isEmpty()) return@mapNotNull null
            var name = base
            var k = 2
            while (name in reservedNames || (!replace && json.has(name))) name = "${base.take(20)} ($k)".also { k++ }
            json.put(name, JSONArray(levels.map { it.coerceIn(EqCore.MIN_DB, EqCore.MAX_DB).toDouble() }))
            name
        }

    private fun customJson() = runCatching { JSONObject(prefs.getString("customPresets", "{}")!!) }.getOrNull() ?: JSONObject()

    private fun storeCustom(json: JSONObject) {
        prefs.edit().putString("customPresets", json.toString()).apply()
        state.update { it.copy(customPresets = customPresets().keys.toList()) }
    }

    /** Guarda la curva actual como preajuste propio (con el mismo nombre, lo reemplaza). */
    fun saveCustomPreset(name: String) {
        val json = customJson()
        val saved = putCustom(json, listOf(name to state.value.levels), replace = true).firstOrNull() ?: return
        storeCustom(json)
        state.update { it.copy(preset = saved) }
        saveProfile()
    }

    /** PRESETS del skin: el preajuste siguiente (incluidos y propios, en orden), y vuelta al principio. */
    fun nextPreset() {
        val all = EqCore.PRESETS.keys.toList() + customPresets().keys
        val i = all.indexOf(state.value.preset)
        usePreset(all[(i + 1) % all.size])
    }

    /**
     * Agrega preajustes propios (p. ej. importados de Winamp); los nombres repetidos llevan un número.
     * Devuelve cuántos se agregaron.
     */
    fun addCustomPresets(presets: List<Pair<String, List<Float>>>): Int {
        val json = customJson()
        val added = putCustom(json, presets, replace = false)
        storeCustom(json)
        return added.size
    }

    fun deleteCustomPreset(name: String) {
        val json = runCatching { JSONObject(prefs.getString("customPresets", "{}")!!) }.getOrNull() ?: return
        json.remove(name)
        prefs.edit().putString("customPresets", json.toString()).apply()
        state.update { it.copy(customPresets = customPresets().keys.toList()) }
    }

    /**
     * Modo guardado. Antes era «Evitar distorsión» sí/no: activado (bajar todo, lo que hacía perder
     * volumen con los graves) pasa a «Equilibrada»; desactivado, a «Volumen».
     */
    private fun savedHeadroom(): EqCore.Headroom =
        prefs.getString("headroom", null)?.let { runCatching { EqCore.Headroom.valueOf(it) }.getOrNull() }
            ?: if (prefs.getBoolean("avoidClipping", true)) EqCore.Headroom.BALANCED else EqCore.Headroom.LOUD

    // Va al final: Kotlin inicializa en orden y el bloque usa propiedades declaradas arriba.
    init {
        state.update {
            it.copy(
                enabled = prefs.getBoolean("enabled", false),
                headroom = savedHeadroom(),
                autoReclaim = prefs.getBoolean("autoReclaim", true),
                loudness = prefs.getBoolean("loudness", false),
                auto = prefs.getBoolean("auto", false),
                surround = prefs.getFloat("surround", 0f),
                speakerProtect = prefs.getBoolean("speakerProtect", true),
                customPresets = customPresets().keys.toList(),
            )
        }
        switchProfile(currentOutput())
        watchVolume()
        if (!state.value.enabled) state.update { it.copy(surroundSupported = probeSurround()) }
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = onOutputChanged()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = onOutputChanged()
        }, main)
        if (state.value.enabled) createEffect()
    }
}
