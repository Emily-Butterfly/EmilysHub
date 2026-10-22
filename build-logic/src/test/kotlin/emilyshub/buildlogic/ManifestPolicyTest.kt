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
            manifestPolicyViolations(manifest),
        )
    }

    @Test
    fun `INTERNET permission is forbidden when requested as uses-permission-sdk-23`() {
        val manifest = mergedManifest(
            permissions = """<uses-permission-sdk-23 android:name="android.permission.INTERNET" />""",
        )

        assertEquals(
            listOf(ManifestViolation.ForbiddenPermission("android.permission.INTERNET")),
            manifestPolicyViolations(manifest),
        )
    }

    @Test
    fun `exported components other than the launcher activity are forbidden`() {
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
            manifestPolicyViolations(manifest),
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

        assertEquals(listOf(ManifestViolation.BackupAllowed), manifestPolicyViolations(manifest))
    }

    @Test
    fun `missing allowBackup counts as allowed because Android defaults it to true`() {
        val manifest = mergedManifest(
            applicationAttributes = """android:dataExtractionRules="@xml/data_extraction_rules"""",
        )

        assertEquals(listOf(ManifestViolation.BackupAllowed), manifestPolicyViolations(manifest))
    }

    @Test
    fun `data extraction rules are required because allowBackup does not stop device transfer`() {
        val manifest = mergedManifest(applicationAttributes = """android:allowBackup="false"""")

        assertEquals(listOf(ManifestViolation.MissingDataExtractionRules), manifestPolicyViolations(manifest))
    }

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
