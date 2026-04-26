package com.wrbug.polymarketbot.controller.copytrading.configs

import com.wrbug.polymarketbot.dto.AccountTemplatesRequest
import com.wrbug.polymarketbot.dto.AccountTemplatesResponse
import com.wrbug.polymarketbot.dto.ApiResponse
import com.wrbug.polymarketbot.dto.CopyTradingCreateRequest
import com.wrbug.polymarketbot.dto.CopyTradingDeleteRequest
import com.wrbug.polymarketbot.dto.CopyTradingDetailRequest
import com.wrbug.polymarketbot.dto.CopyTradingDto
import com.wrbug.polymarketbot.dto.CopyTradingListRequest
import com.wrbug.polymarketbot.dto.CopyTradingListResponse
import com.wrbug.polymarketbot.dto.CopyTradingUpdateRequest
import com.wrbug.polymarketbot.dto.CopyTradingUpdateStatusRequest
import com.wrbug.polymarketbot.dto.FilteredOrderListRequest
import com.wrbug.polymarketbot.dto.FilteredOrderListResponse
import com.wrbug.polymarketbot.dto.FollowSettingsDetailRequest
import com.wrbug.polymarketbot.dto.FollowSettingsResponse
import com.wrbug.polymarketbot.dto.FollowSettingsSaveRequest
import com.wrbug.polymarketbot.dto.LeaderGroupActionRequest
import com.wrbug.polymarketbot.dto.LeaderGroupControlDto
import com.wrbug.polymarketbot.dto.LeaderGroupControlListRequest
import com.wrbug.polymarketbot.dto.LeaderGroupControlListResponse
import com.wrbug.polymarketbot.dto.LeaderGroupControlUpdateRequest
import com.wrbug.polymarketbot.enums.ErrorCode
import com.wrbug.polymarketbot.service.copytrading.configs.CopyTradingService
import com.wrbug.polymarketbot.service.copytrading.configs.FilteredOrderService
import com.wrbug.polymarketbot.service.copytrading.configs.LeaderGroupControlService
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/copy-trading/configs")
class CopyTradingController(
    private val copyTradingService: CopyTradingService,
    private val leaderGroupControlService: LeaderGroupControlService,
    private val filteredOrderService: FilteredOrderService,
    private val messageSource: MessageSource
) {

    private val logger = LoggerFactory.getLogger(CopyTradingController::class.java)

    @PostMapping("/create")
    fun createCopyTrading(@RequestBody request: CopyTradingCreateRequest): ResponseEntity<ApiResponse<CopyTradingDto>> {
        return try {
            if (request.accountId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ACCOUNT_ID_INVALID, messageSource = messageSource))
            }
            if (request.leaderId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_LEADER_ID_INVALID, messageSource = messageSource))
            }
            if (request.templateId != null && request.templateId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_TEMPLATE_ID_INVALID, messageSource = messageSource))
            }

            copyTradingService.createCopyTrading(request).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = { toCopyTradingError(ErrorCode.SERVER_COPY_TRADING_CREATE_FAILED, it) }
            )
        } catch (e: Exception) {
            logger.error("Create copy trading config failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_CREATE_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/list")
    fun getCopyTradingList(@RequestBody request: CopyTradingListRequest): ResponseEntity<ApiResponse<CopyTradingListResponse>> {
        return try {
            copyTradingService.getCopyTradingList(request).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = {
                    logger.error("Fetch copy trading list failed", it)
                    ResponseEntity.ok(
                        ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_LIST_FETCH_FAILED, it.message, messageSource)
                    )
                }
            )
        } catch (e: Exception) {
            logger.error("Fetch copy trading list failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_LIST_FETCH_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/detail")
    fun getCopyTradingDetail(@RequestBody request: CopyTradingDetailRequest): ResponseEntity<ApiResponse<CopyTradingDto>> {
        return try {
            if (request.copyTradingId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_COPY_TRADING_ID_INVALID, messageSource = messageSource))
            }

            copyTradingService.getCopyTradingDetail(request.copyTradingId).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = { toCopyTradingError(ErrorCode.SERVER_COPY_TRADING_LIST_FETCH_FAILED, it) }
            )
        } catch (e: Exception) {
            logger.error("Fetch copy trading detail failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_LIST_FETCH_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/update")
    fun updateCopyTrading(@RequestBody request: CopyTradingUpdateRequest): ResponseEntity<ApiResponse<CopyTradingDto>> {
        return try {
            if (request.copyTradingId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_COPY_TRADING_ID_INVALID, messageSource = messageSource))
            }

            copyTradingService.updateCopyTrading(request).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = { toCopyTradingError(ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED, it) }
            )
        } catch (e: Exception) {
            logger.error("Update copy trading config failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/update-status")
    fun updateCopyTradingStatus(@RequestBody request: CopyTradingUpdateStatusRequest): ResponseEntity<ApiResponse<CopyTradingDto>> {
        return try {
            if (request.copyTradingId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_COPY_TRADING_ID_INVALID, messageSource = messageSource))
            }

            copyTradingService.updateCopyTradingStatus(request).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = { toCopyTradingError(ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED, it) }
            )
        } catch (e: Exception) {
            logger.error("Update copy trading status failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/delete")
    fun deleteCopyTrading(@RequestBody request: CopyTradingDeleteRequest): ResponseEntity<ApiResponse<Unit>> {
        return try {
            if (request.copyTradingId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_COPY_TRADING_ID_INVALID, messageSource = messageSource))
            }

            copyTradingService.deleteCopyTrading(request.copyTradingId).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(Unit)) },
                onFailure = {
                    logger.error("Delete copy trading config failed", it)
                    val errorCode = if (it is IllegalArgumentException) {
                        ErrorCode.PARAM_ERROR
                    } else {
                        ErrorCode.SERVER_COPY_TRADING_DELETE_FAILED
                    }
                    ResponseEntity.ok(ApiResponse.error(errorCode, it.message, messageSource))
                }
            )
        } catch (e: Exception) {
            logger.error("Delete copy trading config failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_DELETE_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/account-templates")
    fun getAccountTemplates(@RequestBody request: AccountTemplatesRequest): ResponseEntity<ApiResponse<AccountTemplatesResponse>> {
        return try {
            if (request.accountId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ACCOUNT_ID_INVALID, messageSource = messageSource))
            }

            copyTradingService.getAccountTemplates(request.accountId).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = {
                    logger.error("Fetch account copy trading list failed", it)
                    val errorCode = if (it is IllegalArgumentException) {
                        ErrorCode.PARAM_ERROR
                    } else {
                        ErrorCode.SERVER_COPY_TRADING_TEMPLATES_FETCH_FAILED
                    }
                    ResponseEntity.ok(ApiResponse.error(errorCode, it.message, messageSource))
                }
            )
        } catch (e: Exception) {
            logger.error("Fetch account copy trading list failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_TEMPLATES_FETCH_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/follow-settings/detail")
    fun getFollowSettings(@RequestBody request: FollowSettingsDetailRequest): ResponseEntity<ApiResponse<FollowSettingsResponse>> {
        return try {
            if (request.copyTradingId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_COPY_TRADING_ID_INVALID, messageSource = messageSource))
            }

            copyTradingService.getFollowSettings(request.copyTradingId).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = {
                    logger.error("Fetch follow settings failed", it)
                    val errorCode = if (it is IllegalArgumentException) {
                        ErrorCode.PARAM_ERROR
                    } else {
                        ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED
                    }
                    ResponseEntity.ok(ApiResponse.error(errorCode, it.message, messageSource))
                }
            )
        } catch (e: Exception) {
            logger.error("Fetch follow settings failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/follow-settings/save")
    fun saveFollowSettings(@RequestBody request: FollowSettingsSaveRequest): ResponseEntity<ApiResponse<FollowSettingsResponse>> {
        return try {
            if (request.copyTradingId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_COPY_TRADING_ID_INVALID, messageSource = messageSource))
            }

            copyTradingService.saveFollowSettings(request.copyTradingId, request.enabled, request.rules).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = {
                    logger.error("Save follow settings failed", it)
                    val errorCode = if (it is IllegalArgumentException) {
                        ErrorCode.PARAM_ERROR
                    } else {
                        ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED
                    }
                    ResponseEntity.ok(ApiResponse.error(errorCode, it.message, messageSource))
                }
            )
        } catch (e: Exception) {
            logger.error("Save follow settings failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/group-controls/list")
    fun getLeaderGroupControls(@RequestBody request: LeaderGroupControlListRequest): ResponseEntity<ApiResponse<LeaderGroupControlListResponse>> {
        return try {
            leaderGroupControlService.listControls(request.leaderIds).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = {
                    logger.error("Fetch leader group controls failed", it)
                    ResponseEntity.ok(
                        ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_LIST_FETCH_FAILED, it.message, messageSource)
                    )
                }
            )
        } catch (e: Exception) {
            logger.error("Fetch leader group controls failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_LIST_FETCH_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/group-controls/restart")
    fun restartLeaderGroup(@RequestBody request: LeaderGroupActionRequest): ResponseEntity<ApiResponse<LeaderGroupControlDto>> {
        return try {
            if (request.leaderId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_LEADER_ID_INVALID, messageSource = messageSource))
            }

            leaderGroupControlService.restartGroup(request.leaderId).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = { toLeaderGroupError(it) }
            )
        } catch (e: Exception) {
            logger.error("Restart leader group failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/group-controls/close")
    fun closeLeaderGroup(@RequestBody request: LeaderGroupActionRequest): ResponseEntity<ApiResponse<LeaderGroupControlDto>> {
        return try {
            if (request.leaderId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_LEADER_ID_INVALID, messageSource = messageSource))
            }

            leaderGroupControlService.closeGroup(request.leaderId).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = { toLeaderGroupError(it) }
            )
        } catch (e: Exception) {
            logger.error("Close leader group failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/group-controls/update")
    fun updateLeaderGroupControl(@RequestBody request: LeaderGroupControlUpdateRequest): ResponseEntity<ApiResponse<LeaderGroupControlDto>> {
        return try {
            if (request.leaderId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_LEADER_ID_INVALID, messageSource = messageSource))
            }

            leaderGroupControlService.updateControl(request).fold(
                onSuccess = { ResponseEntity.ok(ApiResponse.success(it)) },
                onFailure = { toLeaderGroupError(it) }
            )
        } catch (e: Exception) {
            logger.error("Update leader group control failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED, e.message, messageSource))
        }
    }

    @PostMapping("/filtered-orders")
    fun getFilteredOrders(@RequestBody request: FilteredOrderListRequest): ResponseEntity<ApiResponse<FilteredOrderListResponse>> {
        return try {
            if (request.copyTradingId <= 0) {
                return ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_COPY_TRADING_ID_INVALID, messageSource = messageSource))
            }

            ResponseEntity.ok(ApiResponse.success(filteredOrderService.getFilteredOrders(request)))
        } catch (e: Exception) {
            logger.error("Fetch filtered orders failed", e)
            ResponseEntity.ok(ApiResponse.error(ErrorCode.SERVER_ERROR, e.message, messageSource))
        }
    }

    private fun toCopyTradingError(
        defaultErrorCode: ErrorCode,
        throwable: Throwable
    ): ResponseEntity<ApiResponse<CopyTradingDto>> {
        logger.error("Copy trading request failed", throwable)
        val errorCode = when (throwable) {
            is IllegalArgumentException -> ErrorCode.PARAM_ERROR
            is IllegalStateException -> ErrorCode.BUSINESS_ERROR
            else -> defaultErrorCode
        }
        return ResponseEntity.ok(ApiResponse.error(errorCode, throwable.message, messageSource))
    }

    private fun toLeaderGroupError(
        throwable: Throwable
    ): ResponseEntity<ApiResponse<LeaderGroupControlDto>> {
        logger.error("Leader group request failed", throwable)
        val errorCode = when (throwable) {
            is IllegalArgumentException -> ErrorCode.PARAM_ERROR
            is IllegalStateException -> ErrorCode.BUSINESS_ERROR
            else -> ErrorCode.SERVER_COPY_TRADING_UPDATE_FAILED
        }
        return ResponseEntity.ok(ApiResponse.error(errorCode, throwable.message, messageSource))
    }
}
