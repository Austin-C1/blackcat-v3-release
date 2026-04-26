package com.wrbug.polymarketbot.service.system

import com.fasterxml.jackson.databind.ObjectMapper
import com.wrbug.polymarketbot.dto.NotificationConfigData
import com.wrbug.polymarketbot.dto.NotificationConfigDto
import com.wrbug.polymarketbot.dto.NotificationConfigRequest
import com.wrbug.polymarketbot.dto.TelegramConfigData
import com.wrbug.polymarketbot.entity.NotificationConfig
import com.wrbug.polymarketbot.repository.NotificationConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationConfigService(
    private val notificationConfigRepository: NotificationConfigRepository,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(NotificationConfigService::class.java)

    suspend fun getAllConfigs(): List<NotificationConfigDto> {
        return withContext(Dispatchers.IO) {
            notificationConfigRepository.findAll().map { entityToDto(it) }
        }
    }

    suspend fun getConfigsByType(type: String): List<NotificationConfigDto> {
        return withContext(Dispatchers.IO) {
            notificationConfigRepository.findByType(type).map { entityToDto(it) }
        }
    }

    suspend fun getEnabledConfigsByType(type: String): List<NotificationConfigDto> {
        return withContext(Dispatchers.IO) {
            notificationConfigRepository.findByTypeAndEnabled(type, true).map { entityToDto(it) }
        }
    }

    suspend fun getConfigById(id: Long): NotificationConfigDto? {
        return withContext(Dispatchers.IO) {
            notificationConfigRepository.findById(id).orElse(null)?.let { entityToDto(it) }
        }
    }

    @Transactional
    suspend fun createConfig(request: NotificationConfigRequest): Result<NotificationConfigDto> {
        return try {
            validateConfig(request.type, request.config)

            val configJson = objectMapper.writeValueAsString(request.config)
            val config = NotificationConfig(
                type = request.type,
                name = request.name,
                enabled = request.enabled ?: true,
                configJson = configJson
            )

            val saved = withContext(Dispatchers.IO) {
                notificationConfigRepository.save(config)
            }

            Result.success(entityToDto(saved))
        } catch (e: Exception) {
            logger.error("Failed to create notification config: {}", e.message, e)
            Result.failure(e)
        }
    }

    @Transactional
    suspend fun updateConfig(id: Long, request: NotificationConfigRequest): Result<NotificationConfigDto> {
        return try {
            val existing = withContext(Dispatchers.IO) {
                notificationConfigRepository.findById(id).orElse(null)
            } ?: return Result.failure(IllegalArgumentException("Config not found"))

            validateConfig(request.type, request.config)

            val configJson = objectMapper.writeValueAsString(request.config)
            val updated = existing.copy(
                type = request.type,
                name = request.name,
                enabled = request.enabled ?: existing.enabled,
                configJson = configJson,
                updatedAt = System.currentTimeMillis()
            )

            val saved = withContext(Dispatchers.IO) {
                notificationConfigRepository.save(updated)
            }

            Result.success(entityToDto(saved))
        } catch (e: Exception) {
            logger.error("Failed to update notification config: {}", e.message, e)
            Result.failure(e)
        }
    }

    @Transactional
    suspend fun updateEnabled(id: Long, enabled: Boolean): Result<NotificationConfigDto> {
        return try {
            val existing = withContext(Dispatchers.IO) {
                notificationConfigRepository.findById(id).orElse(null)
            } ?: return Result.failure(IllegalArgumentException("Config not found"))

            val updated = existing.copy(
                enabled = enabled,
                updatedAt = System.currentTimeMillis()
            )

            val saved = withContext(Dispatchers.IO) {
                notificationConfigRepository.save(updated)
            }

            Result.success(entityToDto(saved))
        } catch (e: Exception) {
            logger.error("Failed to update notification enabled status: {}", e.message, e)
            Result.failure(e)
        }
    }

    @Transactional
    suspend fun deleteConfig(id: Long): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                notificationConfigRepository.deleteById(id)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete notification config: {}", e.message, e)
            Result.failure(e)
        }
    }

    private fun validateConfig(type: String, config: Map<String, Any>) {
        when (type.lowercase()) {
            "telegram" -> validateTelegramConfig(config)
        }
    }

    private fun validateTelegramConfig(config: Map<String, Any>) {
        val botToken = config["botToken"] as? String
        val chatIds = config["chatIds"]

        require(!botToken.isNullOrBlank()) { "Telegram Bot Token cannot be blank" }
        require(chatIds != null) { "Telegram Chat IDs cannot be blank" }

        val chatIdList = when (chatIds) {
            is List<*> -> chatIds.mapNotNull { it?.toString() }.filter { it.isNotBlank() }
            is String -> chatIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else -> throw IllegalArgumentException("Chat IDs must be a list or a comma-separated string")
        }

        require(chatIdList.isNotEmpty()) { "At least one Chat ID is required" }

        val monitorModeEnabled = config["monitorModeEnabled"]
        require(monitorModeEnabled == null || monitorModeEnabled is Boolean) {
            "monitorModeEnabled must be a boolean"
        }
    }

    private fun entityToDto(entity: NotificationConfig): NotificationConfigDto {
        val configMap = try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(entity.configJson, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            logger.error("Failed to parse notification config JSON: {}", e.message, e)
            emptyMap()
        }

        val configData = when (entity.type.lowercase()) {
            "telegram" -> {
                val botToken = configMap["botToken"]?.toString() ?: ""
                val chatIds = when (val ids = configMap["chatIds"]) {
                    is List<*> -> ids.mapNotNull { it?.toString() }
                    is String -> ids.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    else -> emptyList()
                }
                val monitorModeEnabled = when (val raw = configMap["monitorModeEnabled"]) {
                    is Boolean -> raw
                    is String -> raw.equals("true", ignoreCase = true)
                    else -> false
                }
                NotificationConfigData.Telegram(
                    TelegramConfigData(
                        botToken = botToken,
                        chatIds = chatIds,
                        monitorModeEnabled = monitorModeEnabled
                    )
                )
            }

            else -> NotificationConfigData.Telegram(TelegramConfigData("", emptyList(), false))
        }

        return NotificationConfigDto(
            id = entity.id,
            type = entity.type,
            name = entity.name,
            enabled = entity.enabled,
            config = configData,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
