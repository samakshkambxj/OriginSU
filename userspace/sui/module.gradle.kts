/*
 * This file is part of Sui.
 *
 * Sui is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Sui is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Sui.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2026 Sui Contributors
 */

import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.canRead()) {
    localPropertiesFile.inputStream().use(localProperties::load)
}

val apiRoot = if (localProperties.getProperty("api.useLocal") == "true") {
    localProperties.getProperty("api.dir") ?: "$rootDir/api"
} else {
    "$rootDir/api"
}

apply(from = "$apiRoot/manifest.gradle.kts")

val payloadVersion = rootProject.file("payload-version.txt").readLines()
    .map(String::trim)
    .filter { it.contains('=') && !it.startsWith("#") }
    .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

val gitCommitId = (payloadVersion["upstream_commit"] ?: "unknown").take(8)
val gitCommitCount = payloadVersion["version_code"]?.toIntOrNull() ?: 1

val moduleLibraryName = "sui"
val moduleId = "originsu-sui"
val moduleName = "Origin Sui"
val moduleAuthor = "Sui Contributors, OriginSU"
val moduleDescription = "Sui built into OriginSU: Shizuku API for apps that support it. Requires a working Zygisk provider."
val moduleVersionName = requireNotNull(payloadVersion["version"]) { "payload-version.txt lacks version" }
val moduleLocaleFilters = "en,zh,zh-rCN,zh-rTW"

extra["gitCommitId"] = gitCommitId
extra["gitCommitCount"] = gitCommitCount
extra["moduleLibraryName"] = moduleLibraryName
extra["moduleId"] = moduleId
extra["moduleName"] = moduleName
extra["moduleAuthor"] = moduleAuthor
extra["moduleDescription"] = moduleDescription
extra["moduleVersion"] = "v$moduleVersionName"
extra["moduleVersionCode"] = gitCommitCount
extra["moduleLocaleFilters"] = moduleLocaleFilters
