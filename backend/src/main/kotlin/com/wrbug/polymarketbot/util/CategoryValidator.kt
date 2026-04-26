package com.wrbug.polymarketbot.util

object CategoryValidator {
    
    private val SUPPORTED_CATEGORIES = setOf("sports", "crypto")
    
    private val CATEGORY_MAPPING = mapOf(
        "sports" to "sports",
        "crypto" to "crypto",
        "cryptocurrency" to "crypto",
        "cryptocurrencies" to "crypto"
    )
    
    fun isValid(category: String?): Boolean {
        if (category == null) {
            return false
        }
        
        val categoryLower = category.lowercase()
        if (categoryLower in SUPPORTED_CATEGORIES) {
            return true
        }
        if (categoryLower in CATEGORY_MAPPING.keys) {
            return true
        }
        if (categoryLower.contains("sport")) {
            return true
        }
        if (categoryLower.contains("crypto")) {
            return true
        }
        
        return false
    }
    
    fun normalizeCategory(category: String?): String? {
        if (category == null) {
            return null
        }
        
        val categoryLower = category.lowercase()
        CATEGORY_MAPPING[categoryLower]?.let {
            return it
        }
        if (categoryLower.contains("sport")) {
            return "sports"
        }
        if (categoryLower.contains("crypto")) {
            return "crypto"
        }
        
        return null
    }
    
    fun validate(category: String?) {
        if (!isValid(category)) {
            throw IllegalArgumentException("不支持的分类: $category，仅支持: ${SUPPORTED_CATEGORIES.joinToString(", ")}")
        }
    }
    
    fun getSupportedCategories(): Set<String> {
        return SUPPORTED_CATEGORIES
    }
}

