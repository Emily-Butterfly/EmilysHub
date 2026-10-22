package emilyshub.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Test

class ManifestPolicyTest {

    @Test
    fun `INTERNET permission is forbidden`() {
        val manifest = mergedManifest(
            permissions = """<uses-permission android:name="android.permission.INTERNET" />""",
        )

        assertEquals(
            listOf(ManifestViolation.ForbiddenPermission("android.permission.INTERNET")),
            violations(manifest),
        )
    }

    @Test
    fun `INTERNET permission is forbidden when requested as uses-permission-sdk-23`() {
        val manifest = mergedManifest(
            permissions = """<uses-permission-sdk-23 android:name="android.permission.INTERNET" />""",
        )

        assertEquals(
            listOf(ManifestViolation.ForbiddenPermission("android.permission.INTERNET")),
            violations(manifest),
        )
    }

    @Test
    fun `exported components other than the app's launcher activity are forbidden`() {
        val manifest = mergedManifest(
            components = """
                <receiver
                    android:name="androidx.profileinstaller.ProfileInstallReceiver"
                    android:exported="true"
                    android:permission="android.permission.DUMP" />
                <activity android:name="de.emilyshub.ShareActivity" android:exported="true" />
            """,
        )

        assertEquals(
            listOf(
                ManifestViolation.ExportedComponent("receiver", "androidx.profileinstaller.ProfileInstallReceiver"),
                ManifestViolation.ExportedComponent("activity", "de.emilyshub.ShareActivity"),
            ),
            violations(manifest),
        )
    }

    @Test
    fun `backups must be explicitly disabled`() {
        val manifest = mergedManifest(
            applicationAttributes = """
                android:allowBackup="true"
                android:dataExtractionRules="@xml/data_extraction_rules"
            """,
        )

        assertEquals(listOf(ManifestViolation.BackupAllowed), violations(manifest))
    }

    @Test
    fun `missing allowBackup counts as allowed because Android defaults it to true`() {
        val manifest = mergedManifest(
            applicationAttributes = """android:dataExtractionRules="@xml/data_extraction_rules"""",
        )

        assertEquals(listOf(ManifestViolation.BackupAllowed), violations(manifest))
    }

    @Test
    fun `data extraction rules are required because allowBackup does not stop device transfer`() {
        val manifest = mergedManifest(applicationAttributes = """android:allowBackup="false"""")

        assertEquals(listOf(ManifestViolation.MissingDataExtractionRules), violations(manifest))
    }

    @Test
    fun `a launcher activity added by a library is forbidden too`() {
        val manifest = mergedManifest(
            components = """
                <activity-alias
                    android:name="leakcanary.internal.activity.LeakLauncherActivity"
                    android:exported="true"
                    android:targetActivity="leakcanary.internal.activity.LeakActivity">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity-alias>
            """,
        )

        assertEquals(
            listOf(ManifestViolation.ExportedComponent("activity-alias", "leakcanary.internal.activity.LeakLauncherActivity")),
            violations(manifest),
        )
    }

    private fun violations(manifest: String) =
        manifestPolicyViolations(manifest, launcherActivity = "de.emilyshub.MainActivity")

    /** A merged manifest that satisfies the policy, plus whatever the test adds. */
    private fun mergedManifest(
        permissions: String = "",
        applicationAttributes: String = """
            android:allowBackup="false"
            android:dataExtractionRules="@xml/data_extraction_rules"
        """,
        components: String = "",
    ) = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="de.emilyshub">
            <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
            $permissions
            <application $applicationAttributes>
                <activity android:name="de.emilyshub.MainActivity" android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
                <provider android:name="androidx.startup.InitializationProvider" android:exported="false" />
                $components
            </application>
        </manifest>
    """.trimIndent()
}
