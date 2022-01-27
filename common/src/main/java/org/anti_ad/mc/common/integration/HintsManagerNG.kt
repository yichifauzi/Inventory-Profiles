package org.anti_ad.mc.common.integration

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.anti_ad.mc.common.Log
import org.anti_ad.mc.common.extensions.div
import org.anti_ad.mc.common.extensions.exists
import org.anti_ad.mc.common.extensions.trySwallow
import org.anti_ad.mc.ipn.api.IPNButton
import org.anti_ad.mc.ipn.api.IPNGuiHint
import org.anti_ad.mc.ipn.api.IPNIgnore
import org.anti_ad.mc.ipn.api.IPNPlayerSideOnly
import java.io.InputStream
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlinx.serialization.json.Json as HiddenJson

private val json = HiddenJson {
    ignoreUnknownKeys = true
}

@OptIn(ExperimentalSerializationApi::class)
object HintsManagerNG {

    private val NO_HINTS: HintClassData = HintClassData()

    private const val exampleFileName = "exampleIntegrationHints.json"

    private const val builtInHintsResource = "assets/inventoryprofilesnext/config/ModIntegrationHintsNG.json"

    private const val exampleHintsResource = "assets/inventoryprofilesnext/config/$exampleFileName"

    private lateinit var externalHintsPath: Path

    private val externalConfigs: MutableMap<String, HintClassData> = mutableMapOf()

    private val internalConfigs: MutableMap<String, HintClassData> = mutableMapOf()

    private val effectiveHints: MutableMap<String, HintClassData> = mutableMapOf()

    private fun processConfig(stream: InputStream): Map<String, HintClassData> = json.decodeFromStream<Map<String, HintClassData>>(stream).also {
        trySwallow {
            stream.close()
        }
    }

    fun init(external: Path) {
        reset()
        externalHintsPath = external
        doInit()
    }

    private fun doInit() {
        if (externalHintsPath.isDirectory()) {
            Files.find(externalHintsPath, 1, { p, a ->
                a.isRegularFile && p.fileName.toString().lowercase().endsWith(".json")
            }, FileVisitOption.FOLLOW_LINKS).forEach { f ->
                val id = f.name
                tryLog(id, ::logError) {
                    val data = processConfig(f.inputStream())
                    data.forEach { v ->
                        externalConfigs[v.key] = v.value.also { it.changeId(id) }
                    }
                }
            }
        }
        HintsManagerNG::class.java.classLoader.getResourceAsStream(builtInHintsResource)?.use { input ->
            tryLog("", ::logError) {
                val data: MutableMap<String, Map<String, HintClassData>> = json.decodeFromStream<MutableMap<String, Map<String, HintClassData>>>(input).also {
                    trySwallow {  input.close() }
                }
                data.forEach { ids ->
                    ids.value.forEach { v ->
                        internalConfigs[v.key] = v.value.also { it.changeId(v.key) }
                    }
                }
            }
        }
        externalConfigs.forEach { (name, hintClassData) ->
            effectiveHints.putIfAbsent(name, hintClassData)
        }
        internalConfigs.forEach { (name, hintClassData) ->
            effectiveHints.putIfAbsent(name, hintClassData)
        }
    }

    fun getHints(cl: Class<*>): HintClassData {
        return effectiveHints[cl.name].let {
            it.let {
                if (it != null && !it.force
                    && (cl.isAnnotationPresent(IPNIgnore::class.java) || cl.isAnnotationPresent(IPNPlayerSideOnly::class.java) || cl.isAnnotationPresent(IPNGuiHint::class.java))) {
                    null
                } else {
                    it
                }
            } ?: run {
                val isIgnored = getIgnoredClass(cl)?.also { ignored ->
                    if (ignored != cl) {
                        getHints(ignored)
                    }
                } != null
                val buttonHints: MutableMap<IPNButton, ButtonPositionHint> = mutableMapOf()
                cl.getAnnotationsByType(IPNGuiHint::class.java).forEach { ipnButton ->
                    buttonHints[ipnButton.button] = ButtonPositionHint(ipnButton.horizontalOffset,
                                                                       ipnButton.top,
                                                                       ipnButton.bottom,
                                                                       ipnButton.hide)
                }
                val isIPNPlayerSideOnly = cl.isAnnotationPresent(IPNPlayerSideOnly::class.java)
                val newVal = if (isIgnored || isIPNPlayerSideOnly || buttonHints.isNotEmpty()) {
                    HintClassData(isIgnored, isIPNPlayerSideOnly, buttonHints, false)
                } else {
                    NO_HINTS
                }
                effectiveHints[cl.name] = newVal
                newVal
            }
        }

    }

    private fun getIgnoredClass(container: Class<*>): Class<*>? {
        var sup: Class<*> = container
        while (sup != Object::class.java) {
            if (sup.isAnnotationPresent(IPNIgnore::class.java)) {
                return sup
            }
            sup = sup.superclass
        }
        return null
    }

    private fun reset() {
        externalConfigs.clear()
        internalConfigs.clear()
        effectiveHints.clear()
    }

    fun isPlayerSideOnly(javaClass: Class<*>?): Boolean {
        return javaClass != null && getHints(javaClass).playerSideOnly
    }

    fun upgradeOldConfig(oldConfigFile: Path,
                         newConfigDir: Path) {
        if (oldConfigFile.exists()) {
            trySwallow {
                oldConfigFile.moveTo(newConfigDir / "upgraded-From-Pre-v1.2.5.json")
            }
        }
        val example = newConfigDir / exampleFileName
        if (example.notExists()) {
            HintsManagerNG::class.java.classLoader.getResourceAsStream(exampleHintsResource)?.use { input ->
                trySwallow {
                    example.outputStream().use { output ->
                        input.copyTo(output)
                        output.close()
                    }
                    input.close()
                }
            }
        }
    }

}

private fun logError(th: Throwable,
                     id: String) {
    Log.error("Unable to parse hint file: '$id'. Error: ${th.message}", th)
}

private inline fun <R> tryLog(id: String,
                              onFailure: (Throwable, String) -> R,
                              tryToRun: () -> R): R {
    return try {
        tryToRun()
    } catch (e: Throwable) {
        onFailure(e, id)
    }
}