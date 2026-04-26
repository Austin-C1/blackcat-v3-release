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
                - 跟单配置新增消息筛选，可按机器人分别接收成功订单、失败订单、过滤订单和监控提醒。
                - 机器人可按盘口类型筛选，例如只接收体育或加密相关消息。
                - 公告内容已重新整理，旧的空公告不再展示。

                ## 使用影响
                - 原来的消息推送机器人仍然可用。
                - 未设置消息筛选的跟单配置，继续按原来的全局规则推送。
                - 已设置消息筛选的跟单配置，只会推送给匹配的机器人。
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

                ## 跟单消息筛选
                新增或编辑跟单配置时，可以在「消息筛选」里添加多个机器人。

                可选筛选项：
                - 盘口类型：全部、体育、加密
                - 消息类型：全部、成功订单、失败订单、过滤订单、监控提醒

                示例：
                - 查理机器人选择「体育 + 监控提醒」，只接收体育相关监控。
                - 威廉机器人选择「加密 + 成功订单」，只接收加密相关成交。
                - 不选盘口类型或消息类型，表示该项不过滤。
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
                - 每个 Leader 后面可以放多个机器人，按体育、加密、成功订单、失败订单分开接收。
                - 大额投注监控建议使用单独机器人，方便快速查看高金额盘口变化。
                - Telegram 测试按钮可先确认机器人和 Chat ID 是否可用。
                - 跟单配置名称建议写清楚用途，例如「查理-体育监控」或「威廉-加密成交」。
                - 如果某个机器人收不到消息，先检查是否启用、Chat ID 是否正确、筛选条件是否过窄。
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
