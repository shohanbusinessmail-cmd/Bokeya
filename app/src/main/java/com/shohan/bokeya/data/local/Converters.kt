package com.shohan.bokeya.data.local

import androidx.room.TypeConverter
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.CategoryKind
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.InstallmentFrequency
import com.shohan.bokeya.domain.model.ItemUnit
import com.shohan.bokeya.domain.model.PaymentMethod

/**
 * Enums are persisted by **name**, not ordinal: reordering an enum constant
 * would silently corrupt every existing row if ordinals were stored.
 * Unknown names decode to a safe default so a downgraded app never crashes.
 */
class Converters {

    @TypeConverter
    fun accountTypeToString(value: AccountType): String = value.name

    @TypeConverter
    fun stringToAccountType(value: String): AccountType =
        runCatching { AccountType.valueOf(value) }.getOrDefault(AccountType.SHOP)

    @TypeConverter
    fun directionToString(value: DebtDirection?): String? = value?.name

    @TypeConverter
    fun stringToDirection(value: String?): DebtDirection? =
        value?.let { runCatching { DebtDirection.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun frequencyToString(value: InstallmentFrequency?): String? = value?.name

    @TypeConverter
    fun stringToFrequency(value: String?): InstallmentFrequency? =
        value?.let { runCatching { InstallmentFrequency.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun methodToString(value: PaymentMethod): String = value.name

    @TypeConverter
    fun stringToMethod(value: String): PaymentMethod =
        runCatching { PaymentMethod.valueOf(value) }.getOrDefault(PaymentMethod.CASH)

    @TypeConverter
    fun categoryKindToString(value: CategoryKind): String = value.name

    @TypeConverter
    fun stringToCategoryKind(value: String): CategoryKind =
        runCatching { CategoryKind.valueOf(value) }.getOrDefault(CategoryKind.EXPENSE)

    @TypeConverter
    fun unitToString(value: ItemUnit?): String? = value?.name

    @TypeConverter
    fun stringToUnit(value: String?): ItemUnit? =
        value?.let { runCatching { ItemUnit.valueOf(it) }.getOrNull() }
}
