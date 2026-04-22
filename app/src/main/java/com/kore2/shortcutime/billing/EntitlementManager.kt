package com.kore2.shortcutime.billing

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import java.util.Calendar

class EntitlementManager(context: Context) {

    private val prefs = context.getSharedPreferences("entitlement_prefs", Context.MODE_PRIVATE)

    /**
     * Synchronous check of Pro status from the local SharedPreferences cache.
     * Updated after every successful purchase or restore.
     * Safe to call from any click listener — no coroutine needed.
     */
    fun isPro(): Boolean = prefs.getBoolean(KEY_IS_PRO, false)

    private fun updateProCache(isPro: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
    }

    /**
     * Initiates purchase flow for the Pro lifetime product.
     * Returns PurchaseResult.Success, .Cancelled, or .Error
     */
    suspend fun purchase(activity: Activity): PurchaseResult {
        return try {
            val offerings = Purchases.sharedInstance.awaitOfferings()
            val pkg = offerings.current?.availablePackages?.firstOrNull()
                ?: return PurchaseResult.Error("구매 가능한 상품이 없습니다")
            Purchases.sharedInstance.awaitPurchase(
                PurchaseParams.Builder(activity, pkg).build()
            )
            updateProCache(true)
            PurchaseResult.Success
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) {
                PurchaseResult.Cancelled
            } else {
                PurchaseResult.Error(e.error.message ?: "구매 중 오류")
            }
        } catch (e: PurchasesException) {
            PurchaseResult.Error(e.error.message ?: "구매 중 오류")
        } catch (e: Exception) {
            PurchaseResult.Error(e.message ?: "알 수 없는 오류")
        }
    }

    suspend fun restorePurchases(): RestoreResult {
        return try {
            val customerInfo = Purchases.sharedInstance.awaitRestore()
            if (customerInfo.entitlements[BillingConstants.ENTITLEMENT_ID]?.isActive == true) {
                updateProCache(true)
                RestoreResult.Restored
            } else {
                RestoreResult.NothingToRestore
            }
        } catch (e: PurchasesException) {
            RestoreResult.Error(e.error.message ?: "복원 중 오류")
        } catch (e: Exception) {
            RestoreResult.Error(e.message ?: "복원 중 오류")
        }
    }

    // Monthly AI usage tracking via SharedPreferences (no RC involvement)

    fun getMonthlyAiUsage(): Int {
        val storedMonth = prefs.getString(KEY_AI_MONTH, "") ?: ""
        return if (storedMonth == currentYearMonth()) {
            prefs.getInt(KEY_AI_COUNT, 0)
        } else {
            0
        }
    }

    fun incrementMonthlyAiUsage() {
        val currentMonth = currentYearMonth()
        val storedMonth = prefs.getString(KEY_AI_MONTH, "") ?: ""
        val current = if (storedMonth == currentMonth) prefs.getInt(KEY_AI_COUNT, 0) else 0
        prefs.edit()
            .putString(KEY_AI_MONTH, currentMonth)
            .putInt(KEY_AI_COUNT, current + 1)
            .apply()
    }

    private fun currentYearMonth(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
    }

    sealed class PurchaseResult {
        object Success : PurchaseResult()
        object Cancelled : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
    }

    sealed class RestoreResult {
        object Restored : RestoreResult()
        object NothingToRestore : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }

    companion object {
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_AI_MONTH = "ai_usage_month"
        private const val KEY_AI_COUNT = "ai_usage_count"
    }
}
