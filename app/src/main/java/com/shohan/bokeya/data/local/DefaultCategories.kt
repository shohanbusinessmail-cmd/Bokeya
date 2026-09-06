package com.shohan.bokeya.data.local

import com.shohan.bokeya.data.local.entity.CategoryEntity
import com.shohan.bokeya.domain.model.CategoryKind

/**
 * Built-in income/expense categories seeded on first launch.
 *
 * [CategoryEntity.builtInKey] is the stable identity — the display name is
 * seeded in Bangla but the user may rename it, and re-seeding must not create
 * duplicates. Colours are chosen to stay legible on both light and dark charts.
 */
object DefaultCategories {

    data class Seed(
        val key: String,
        val name: String,
        val iconKey: String,
        val color: Int,
    )

    val INCOME = listOf(
        Seed("income_salary", "বেতন", "payments", 0xFF10B981.toInt()),
        Seed("income_business", "ব্যবসা", "storefront", 0xFF3B82F6.toInt()),
        Seed("income_freelance", "ফ্রিল্যান্স", "laptop", 0xFF8B5CF6.toInt()),
        Seed("income_rent", "ভাড়া", "home_work", 0xFF0EA5E9.toInt()),
        Seed("income_gift", "উপহার", "card_giftcard", 0xFFEC4899.toInt()),
        Seed("income_other", "অন্যান্য", "more_horiz", 0xFF64748B.toInt()),
    )

    val EXPENSE = listOf(
        Seed("expense_food", "খাবার", "restaurant", 0xFFF59E0B.toInt()),
        Seed("expense_bazar", "বাজার", "shopping_basket", 0xFF10B981.toInt()),
        Seed("expense_transport", "যাতায়াত", "directions_bus", 0xFF3B82F6.toInt()),
        Seed("expense_home", "বাসা", "house", 0xFF8B5CF6.toInt()),
        Seed("expense_health", "চিকিৎসা", "medical_services", 0xFFEF4444.toInt()),
        Seed("expense_education", "শিক্ষা", "school", 0xFF0EA5E9.toInt()),
        Seed("expense_bill", "বিল", "receipt_long", 0xFFF97316.toInt()),
        Seed("expense_shopping", "কেনাকাটা", "shopping_bag", 0xFFEC4899.toInt()),
        Seed("expense_entertainment", "বিনোদন", "movie", 0xFF14B8A6.toInt()),
        Seed("expense_other", "অন্যান্য", "more_horiz", 0xFF64748B.toInt()),
    )

    /** Fallback bucket used when a category is deleted or missing. */
    const val OTHER_EXPENSE_KEY = "expense_other"
    const val OTHER_INCOME_KEY = "income_other"

    fun asEntities(): List<CategoryEntity> = buildList {
        INCOME.forEachIndexed { index, seed ->
            add(
                CategoryEntity(
                    name = seed.name,
                    kind = CategoryKind.INCOME,
                    builtInKey = seed.key,
                    iconKey = seed.iconKey,
                    colorArgb = seed.color,
                    sortOrder = index,
                    isDefault = true,
                ),
            )
        }
        EXPENSE.forEachIndexed { index, seed ->
            add(
                CategoryEntity(
                    name = seed.name,
                    kind = CategoryKind.EXPENSE,
                    builtInKey = seed.key,
                    iconKey = seed.iconKey,
                    colorArgb = seed.color,
                    sortOrder = index,
                    isDefault = true,
                ),
            )
        }
    }
}
