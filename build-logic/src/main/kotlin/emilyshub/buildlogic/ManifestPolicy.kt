package emilyshub.buildlogic

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

sealed interface ManifestViolation {
    data class ForbiddenPermission(val permission: String) : ManifestViolation
    data class ExportedComponent(val kind: String, val name: String) : ManifestViolation
    data object BackupAllowed : ManifestViolation
    data object MissingDataExtractionRules : ManifestViolation
}

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

private val forbiddenPermissions = setOf("android.permission.INTERNET")

/**
 * Checks a merged manifest against SPEC.md §9. [launcherActivity] is the fully qualified name of
 * the app's own launcher, the only component allowed to be exported.
 */
fun manifestPolicyViolations(manifestXml: String, launcherActivity: String): List<ManifestViolation> {
    val manifest = parse(manifestXml)
    val application = manifest.children("application").single()
    return forbiddenPermissionsIn(manifest) +
        exportedComponentsIn(application, except = launcherActivity) +
        backupViolationsIn(application)
}

private fun backupViolationsIn(application: Element) = buildList {
    // An absent android:allowBackup means true.
    if (application.androidAttr("allowBackup") != "false") add(ManifestViolation.BackupAllowed)
    // From Android 12, allowBackup=false no longer prevents device-to-device transfer.
    if (application.androidAttr("dataExtractionRules").isEmpty()) add(ManifestViolation.MissingDataExtractionRules)
}

private fun forbiddenPermissionsIn(manifest: Element) =
    manifest.children("uses-permission", "uses-permission-sdk-23")
        .map { it.androidAttr("name") }
        .filter { it in forbiddenPermissions }
        .map { ManifestViolation.ForbiddenPermission(it) }

private fun exportedComponentsIn(application: Element, except: String) =
    application.children("activity", "activity-alias", "service", "receiver", "provider")
        .filter { it.androidAttr("exported") == "true" && it.androidAttr("name") != except }
        .map { ManifestViolation.ExportedComponent(it.tagName, it.androidAttr("name")) }

private fun parse(xml: String): Element {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    return factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
}

private fun Element.children(vararg tagNames: String): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).map { nodes.item(it) }
        .filterIsInstance<Element>()
        .filter { it.tagName in tagNames }
}

private fun Element.androidAttr(name: String): String = getAttributeNS(ANDROID_NS, name)
