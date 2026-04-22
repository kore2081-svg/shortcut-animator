package com.kore2.shortcutime.billing

object BillingConstants {
    /** RevenueCat 대시보드 → API Keys → Public SDK Key */
    const val REVENUECAT_API_KEY = "YOUR_REVENUECAT_ANDROID_API_KEY"

    /** Google Play Console에 등록한 인앱 상품 ID */
    const val PRODUCT_ID = "pro_lifetime"

    /** RevenueCat Entitlement ID */
    const val ENTITLEMENT_ID = "pro"

    // ── Free tier limits ──────────────────────────────────────────────────
    const val FREE_MAX_FOLDERS = 2
    const val FREE_MAX_SHORTCUTS_PER_FOLDER = 10
    const val FREE_AI_MONTHLY_CAP = 20
}
