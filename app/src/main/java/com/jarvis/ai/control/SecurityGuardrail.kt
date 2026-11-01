package com.jarvis.ai.control

/**
 * Production Security & Privacy Guardrail for Project JARVIS.
 * Strictly blocks autonomous screen tapping and sensitive data logging
 * when protected financial, banking, or password entry apps are active.
 */
object SecurityGuardrail {

    // Blacklisted package names that must never be autonomously touched
    private val BLACKLISTED_FINANCIAL_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay (Tez)
        "net.one97.paytm",                        // Paytm
        "com.phonepe.app",                       // PhonePe
        "in.org.npci.upiapp",                    // BHIM UPI
        "com.sbi.lotusintouch",                  // YONO SBI
        "com.icicibank.mobile",                  // iMobile ICICI
        "com.hdfcbank.android",                  // HDFC Bank MobileBanking
        "com.axis.mobile",                       // Axis Mobile
        "com.kotak.omni"                         // Kotak 811
    )

    /**
     * Returns true if the package is safe for autonomous operations.
     */
    fun isPackageSafe(packageName: String?): Boolean {
        if (packageName == null) return false
        val cleanPkg = packageName.trim().lowercase()

        for (blacklisted in BLACKLISTED_FINANCIAL_PACKAGES) {
            if (cleanPkg.contains(blacklisted.lowercase())) {
                return false
            }
        }
        return true
    }

    /**
     * Checks if a targeted UI element text might represent sensitive credentials (PIN/Password).
     */
    fun isSensitiveElement(elementText: String?): Boolean {
        if (elementText == null) return false
        val lower = elementText.lowercase()
        return lower.contains("pin") ||
                lower.contains("password") ||
                lower.contains("cvv") ||
                lower.contains("otp")
    }
}
