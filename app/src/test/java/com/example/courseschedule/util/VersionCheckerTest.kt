package com.example.courseschedule.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本比较逻辑单测（仅调用纯函数 isNewer，不触发网络） */
class VersionCheckerTest {

    @Test
    fun detectsNewerPatchAndMinor() {
        assertTrue(VersionChecker.isNewer("v2.11", "2.10"))
        assertTrue(VersionChecker.isNewer("v3.0", "2.99"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(VersionChecker.isNewer("v2.11", "2.11"))
        assertFalse(VersionChecker.isNewer("2.11", "v2.11"))
    }

    @Test
    fun olderOrInvalidIsNotNewer() {
        assertFalse(VersionChecker.isNewer("2.9", "2.10"))
        assertFalse(VersionChecker.isNewer("abc", "2.10"))
    }
}
