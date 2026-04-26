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
                - 消息推送的机器人配置新增「监控分类」和「消息类型」筛选。
                - 机器人列表现在可以直接调整监控分类和消息类型，清空选项就是接收全部。
                - 每个机器人可按体育、加密、成功订单、失败订单、过滤订单、监控提醒分别接收消息。
                - 同向提醒、反向提醒也会跟随机器人监控分类筛选。
                - 公告内容已重新整理，旧的空公告不再展示。

                ## 使用影响
                - 原来的消息推送机器人仍然可用。
                - 机器人不选择监控分类或消息类型时，默认接收全部。
                - 机器人选择筛选条件后，只会接收匹配的监控或跟单消息。
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
                进入「系统管理 → 消息推送」，新增或编辑 Telegram 机器人，在机器人配置后面设置「监控分类」和「消息类型」。
                也可以直接在机器人列表的「监控分类」和「消息类型」列里调整。

                可选筛选项：
                - 监控分类：体育、加密
                - 消息类型：成功订单、失败订单、过滤订单、监控提醒（含同向、反向）

                示例：
                - 查理机器人选择「体育 + 监控提醒」，只接收体育相关监控，包括体育同向/反向提醒。
                - 威廉机器人选择「加密 + 成功订单」，只接收加密相关成交。
                - 不选监控分类或消息类型，表示该项不过滤。
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
                - 可以创建多个 Telegram 机器人，分别按体育、加密、成功订单、失败订单分开接收。
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
