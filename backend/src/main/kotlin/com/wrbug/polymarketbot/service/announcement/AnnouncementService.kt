package com.wrbug.polymarketbot.service.announcement

import com.wrbug.polymarketbot.dto.AnnouncementDto
import com.wrbug.polymarketbot.dto.AnnouncementListResponse
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AnnouncementService {

    private val announcements = listOf(
        announcement(
            id = 2026041701L,
            title = "黑猫V1 本地版使用说明",
            body = """
                # 黑猫V1 本地版使用说明

                更新时间：2026-04-17

                - 当前版本只展示黑猫V1自己的公告内容，不再同步外部项目公告。
                - 登录后默认进入账户管理，方便直接导入钱包和查看账户状态。
                - 公告页保留给本地版说明、风险提醒和版本记录使用。

                ## 当前可用页面
                - 账户管理：导入、编辑、删除钱包
                - 跟单交易：配置 Leader 和跟单规则
                - 仓位管理：查看持仓、卖出、赎回
                - 系统管理：查看运行状态和基础设置
            """.trimIndent(),
            createdAt = Instant.parse("2026-04-17T09:30:00Z").toEpochMilli()
        ),
        announcement(
            id = 2026041601L,
            title = "黑猫V1 版本说明",
            body = """
                # 黑猫V1 版本说明

                更新时间：2026-04-16

                ## 本轮已完成
                - 仓位和赎回页面的高频重复请求已压掉
                - 首屏包体积已拆分，打开速度明显改善
                - 数据库启动时的旧提醒已处理
                - 关键并发和缓存问题已完成修正

                ## 当前建议
                - 先导入钱包并完成基础配置
                - 首次使用前检查代理、Builder 和跟单参数
                - 正式使用前先用小资金跑一轮验证
            """.trimIndent(),
            createdAt = Instant.parse("2026-04-16T08:00:00Z").toEpochMilli()
        ),
        announcement(
            id = 2026041501L,
            title = "黑猫V1 使用提醒",
            body = """
                # 黑猫V1 使用提醒

                更新时间：2026-04-15

                - 钱包私钥、Builder 凭证和代理配置都属于敏感信息，只在可信环境内维护。
                - 调整跟单参数后，先检查账户余额、代理状态和实时连接是否正常。
                - 卖出、赎回和自动暂停规则建议先用一组测试账户验证，再扩大到正式账户。
            """.trimIndent(),
            createdAt = Instant.parse("2026-04-15T07:45:00Z").toEpochMilli()
        )
    )

    suspend fun getAnnouncementList(forceRefresh: Boolean = false): Result<AnnouncementListResponse> {
        val list = if (forceRefresh) announcements else announcements
        return Result.success(
            AnnouncementListResponse(
                list = list,
                hasMore = false,
                total = list.size
            )
        )
    }

    suspend fun getAnnouncementDetail(id: Long?, forceRefresh: Boolean = false): Result<AnnouncementDto> {
        val list = if (forceRefresh) announcements else announcements
        val announcement = if (id == null) {
            list.firstOrNull()
        } else {
            list.find { it.id == id }
        } ?: return Result.failure(IllegalArgumentException("公告不存在"))

        return Result.success(announcement)
    }

    private fun announcement(
        id: Long,
        title: String,
        body: String,
        createdAt: Long
    ): AnnouncementDto {
        return AnnouncementDto(
            id = id,
            title = title,
            body = body,
            author = "黑猫V1",
            authorAvatarUrl = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            reactions = null
        )
    }
}
