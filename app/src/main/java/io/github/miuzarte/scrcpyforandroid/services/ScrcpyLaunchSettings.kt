package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyProfiles
import io.github.miuzarte.scrcpyforandroid.storage.Storage

/** Main screen and shortcut use the same profile and server settings. */
internal object ScrcpyLaunchSettings {
    data class Snapshot(val profileId: String, val options: ClientOptions, val session: Scrcpy.SessionConfig, val activateFlipInner: Boolean)

    fun profileId(target: ConnectionTarget?, shortcuts: DeviceShortcuts, selected: String): String =
        target?.let { target -> shortcuts.firstOrNull { it.matchesAddress(target) }?.scrcpyProfileId } ?: selected

    fun bundle(profileId: String, global: ScrcpyOptions.Bundle, profiles: ScrcpyProfiles.State): ScrcpyOptions.Bundle =
        if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID) global
        else profiles.profiles.firstOrNull { it.id == profileId }?.bundle ?: global

    fun session(settings: AppSettings.Bundle): Scrcpy.SessionConfig = Scrcpy.SessionConfig(
        customServerUri = settings.customServerUri.ifBlank { null },
        serverVersion = settings.customServerVersion.ifBlank { Scrcpy.DEFAULT_SERVER_VERSION },
        serverRemotePath = settings.serverRemotePath.ifBlank { AppSettings.SERVER_REMOTE_PATH.defaultValue },
        lowLatency = settings.lowLatency,
    )

    suspend fun load(profileId: String): Snapshot {
        val configured = bundle(profileId, Storage.scrcpyOptions.loadBundle(), Storage.scrcpyProfiles.loadState())
        val app = Storage.appSettings.loadBundle()
        return Snapshot(profileId, Storage.scrcpyOptions.toClientOptions(configured).fix(), session(app), app.activateFlipInner)
    }
}
