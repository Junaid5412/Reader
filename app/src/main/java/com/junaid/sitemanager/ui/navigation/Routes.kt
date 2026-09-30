package com.junaid.sitemanager.ui.navigation

sealed class Route(val route: String) {
    object Dashboard : Route("dashboard")
    object Sites : Route("sites")
    object SiteEdit : Route("site_edit?siteId={siteId}") {
        const val PATTERN = "site_edit?siteId={siteId}"
        fun path(siteId: Long) = "site_edit?siteId=$siteId"
    }

    object Browser : Route("browser") {
        const val ARG_URL = "url"
        const val PATTERN = "browser?url={url}"
        fun path(url: String) = "browser?url=${encode(url)}"
        fun encode(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
        fun decode(v: String?) = v?.let { java.net.URLDecoder.decode(it, "UTF-8") }.orEmpty()
    }

    object Files : Route("files")
    object Logs : Route("logs")
    object Settings : Route("settings")

    object About : Route("about")
}
