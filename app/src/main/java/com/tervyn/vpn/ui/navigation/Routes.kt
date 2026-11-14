package com.tervyn.vpn.ui.navigation

/** Navigation destinations. The three tabs are top-level; everything else is pushed on top of them. */
object Routes {
    const val HOME = "home"
    const val SERVERS = "servers"
    const val SETTINGS = "settings"

    const val SERVER = "server/{id}"
    fun server(id: String) = "server/$id"
    const val ADD_SERVER = "add_server"
    const val SUBSCRIPTIONS = "subscriptions"
    const val DNS = "dns"
    const val SITES = "sites"
    const val SPLIT = "split"
    const val APP_LOCK = "app_lock"
    const val NOTIFICATIONS = "notifications"
    const val IP = "ip"
    const val SPEED = "speed"
    const val DIAGNOSTICS = "diagnostics"
    const val HISTORY = "history"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
    const val PRIVACY = "privacy"
    const val BACKUP = "backup"

    val tabs = listOf(HOME, SERVERS, SETTINGS)
}
