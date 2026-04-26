package com.wrbug.polymarketbot.service.system

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class UpdatePackageSafetyTest {

    @Test
    fun `program files are allowed to be updated`() {
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("backend/build/libs/blackcat-v3-backend-1.0.1.jar"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("frontend/dist/index.html"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("frontend/dist/assets/index.js"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("scripts/serve-blackcat-frontend.ps1"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("launch-blackcat.ps1"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath(".tools/jdk-17.0.18+8/bin/java.exe"))
    }

    @Test
    fun `user data and config paths are never overwritten`() {
        assertFalse(UpdatePackageSafety.isAllowedProgramPath(".env"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("config/local.json"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("data/mysql"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("logs/backend.log"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("backups/update.zip"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("backend-live.out.log"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("../outside.txt"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("/absolute/path.txt"))
    }

    @Test
    fun `semantic versions compare correctly`() {
        assertTrue(UpdateVersionComparator.isNewer("3.0.2", "3.0.1"))
        assertTrue(UpdateVersionComparator.isNewer("v3.1.0", "3.0.9"))
        assertFalse(UpdateVersionComparator.isNewer("3.0.1", "3.0.1"))
        assertFalse(UpdateVersionComparator.isNewer("3.0.0", "3.0.1"))
    }

    @Test
    fun `apply script uses encoded paths and real file list newlines`() {
        val script = UpdateApplyScriptBuilder.render(
            appRoot = Path.of("C:/Users/kesul/Desktop/新建文件夹/_tmp_PolyHermes"),
            packageRoot = Path.of("C:/Users/kesul/Desktop/新建文件夹/_tmp_PolyHermes/updates/work-v3.0.1"),
            backupRoot = Path.of("C:/Users/kesul/Desktop/新建文件夹/_tmp_PolyHermes/backups/update-v3.0.1"),
            files = listOf(
                "backend/build/libs/blackcat-v3-backend-3.0.1.jar",
                "frontend/dist/index.html"
            ),
            backendPid = 12345
        )

        assertTrue(script.contains("[Convert]::FromBase64String"))
        assertFalse(script.contains("新建文件夹"))
        assertFalse(script.contains(",`n"))
        assertTrue(script.contains("'backend/build/libs/blackcat-v3-backend-3.0.1.jar',"))
        assertTrue(script.contains("'frontend/dist/index.html'"))
        assertTrue(script.contains("Stop-Process -Id 12345"))
    }
}
