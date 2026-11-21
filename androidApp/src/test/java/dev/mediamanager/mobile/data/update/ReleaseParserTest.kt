package dev.mediamanager.mobile.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReleaseParserTest {

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        asset: String? = "MediaManager-$tag-arm64.apk",
    ) = """
        {
          "tag_name": "$tag",
          "html_url": "https://github.com/nassih-yassine/MediaManager/releases/tag/$tag",
          "body": "Notes for $tag",
          "published_at": "2026-11-20T10:00:00Z",
          "draft": $draft,
          "prerelease": $prerelease,
          "assets": [${asset?.let { """{"name":"$it","browser_download_url":"https://example.test/$it"}""" } ?: ""}]
        }
    """.trimIndent()

    @Test
    fun `picks the highest android release with an arm64 apk`() {
        val json = "[${release("android-v0.1.1")},${release("android-v0.2.0")},${release("android-v0.1.9")}]"

        val info = ReleaseParser.parse(json)!!

        assertEquals("android-v0.2.0", info.versionTag)
        assertEquals("0.2.0", info.versionName)
        assertEquals("https://example.test/MediaManager-android-v0.2.0-arm64.apk", info.apkUrl)
    }

    @Test
    fun `drafts and prereleases are invisible`() {
        val json = "[${release(
            "android-v0.1.1",
        )},${release("android-v0.2.0", draft = true)},${release("android-v0.3.0", prerelease = true)}]"

        assertEquals("android-v0.1.1", ReleaseParser.parse(json)!!.versionTag)
    }

    @Test
    fun `non android and unparsable tags are ignored`() {
        val json = "[${release("v0.1.2")},${release("android-v0.1.1")},${release("nightly")}]"

        assertEquals("android-v0.1.1", ReleaseParser.parse(json)!!.versionTag)
    }

    @Test
    fun `a release without an apk still parses, with a null asset`() {
        val info = ReleaseParser.parse("[${release("android-v0.2.0", asset = null)}]")!!

        assertNull(info.apkUrl)
        assertEquals("https://github.com/nassih-yassine/MediaManager/releases/tag/android-v0.2.0", info.releaseUrl)
    }

    @Test
    fun `empty and malformed payloads are null`() {
        assertNull(ReleaseParser.parse("[]"))
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("{}"))
    }
}
