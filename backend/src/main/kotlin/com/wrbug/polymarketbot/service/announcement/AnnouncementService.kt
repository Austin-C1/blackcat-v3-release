package com.wrbug.polymarketbot.service.announcement

import com.wrbug.polymarketbot.dto.AnnouncementDto
import com.wrbug.polymarketbot.dto.AnnouncementListResponse
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AnnouncementService {

    private val announcements = listOf(
        announcement(
            id = 2026042603L,
            title = "本次更新内容",
            body = """
                # 本次更新内容

                更新时间：2026-04-26

                ## 已更新
                - 盘口投注额查询已归入系统管理下的消息推送机器人相关页面。
                - 盘口查询页保留页面入口，并新增查询机器人选择。
                - 消息推送的机器人配置改为按 Leader 自定义分组筛选监控消息。
                - Leader 分组筛选现在包含在「监控模式」下面，只影响监控推送、同向提醒、反向提醒。
                - 一个机器人可以负责一个或多个 Leader 分组，不选择分组表示接收全部监控 Leader。
                - 公告内容已重新整理，旧的空公告不再展示。

                ## 使用影响
                - 原来的消息推送机器人仍然可用。
                - 大额投注监控和盘口查询机器人不受 Leader 分组筛选影响。
                - 只要 Leader 管理里填写了自定义分组，就可以在监控机器人后面选择该分组。
            """.trimIndent(),
            createdAt = Instant.parse("2026-04-26T09:00:00Z").toEpochMilli()
        ),
        announcement(
            id = 2026042602L,
            title = "机器人和筛选功能使用说明",
            body = """
                # 机器人和筛选功能使用说明

                ## 盘口投注额查询机器人
                进入「系统管理 → 盘口投注额查询」，在「查询机器人」里选择允许响应盘口查询指令的机器人。

                Telegram 里可以发送：
                - /盘口 Wild vs Stars
                - 盘口 Trump
                - /market World Cup

                ## 机器人消息筛选
                先进入「Leader 管理」，给需要分类的 Leader 填写「自定义分组」。
                再进入「系统管理 → 消息推送」，打开某个机器人的「监控模式」，在下面的「Leader 分组」里选择它负责的分组。

                筛选规则：
                - 不选择 Leader 分组：接收全部监控 Leader。
                - 选择一个或多个 Leader 分组：只接收这些分组的监控推送、同向提醒、反向提醒。
                - 这个筛选只属于监控模式，不影响普通订单通知、大额投注监控和盘口查询机器人。

                示例：
                - 查理机器人选择「体育组」，只接收体育组 Leader 的监控提醒。
                - 威廉机器人不选择 Leader 分组，就接收全部监控 Leader。
                - 大额投注监控建议继续使用单独机器人，避免和 Leader 监控混在一起。
            """.trimIndent(),
            createdAt = Instant.parse("2026-04-26T08:50:00Z").toEpochMilli()
        ),
        announcement(
            id = 2026042601L,
            title = "当前限制和使用技巧",
            body = """
                # 当前限制和使用技巧

                ## 暂未开放下单功能
                当前版本暂时没有开放直接下单功能。请在消息推送设置里打开监控模式，用机器人接收监控提醒。

                ## 建议用法
                - 把查询机器人和监控机器人分开，避免查询消息和监控提醒混在一起。
                - 可以创建多个 Telegram 机器人，分别按 Leader 自定义分组接收监控消息。
                - 大额投注监控建议使用单独机器人，方便快速查看高金额盘口变化。
                - Telegram 测试按钮可先确认机器人和 Chat ID 是否可用。
                - Leader 自定义分组建议写清楚用途，例如「体育组」「政治组」「重点观察」。
                - 如果某个机器人收不到消息，先检查是否启用、是否打开监控模式、Chat ID 是否正确、Leader 分组是否选错。
                - 监控模式打开后，机器人只负责提醒，不会替你直接下单。
            """.trimIndent(),
            createdAt = Instant.parse("2026-04-26T08:40:00Z").toEpochMilli()
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
            author = "黑猫V3",
            authorAvatarUrl = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            reactions = null
        )
    }
}
