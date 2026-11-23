package com.vcampro.ultimate.core

import com.vcampro.ultimate.BuildConfig

/**
 * Release coordinates used by the in-app update check and the About screen.
 * They point at the public distribution repository.
 */
object AppInfo {
    const val GITHUB_OWNER = "MPV999"
    const val GITHUB_REPO = "vcam-pro-ultimate"
    const val WEBSITE_URL = "https://mpv999.github.io/vcam-pro-ultimate/"
    const val RELEASES_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val PRIVACY_URL = "$WEBSITE_URL#privacy"

    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE
}
