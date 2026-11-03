package com.mobile.enerjeep.data


enum class UserRole {
    Superadmin,
    Admin,
    Driver;

    companion object {
        // Mga istasyon ng admin - ito ang pagpipilian sa "Tungkulin" na
        // "Admin - Marilao" at "Admin - Monumento" (CreateAccountScreen).
        // Ginagamit din ang adminStation para sa authorization ng queue.
        const val STATION_MARILAO = "marilao"
        const val STATION_MONUMENTO = "monumento"
    }

    /**
     * Display label ng tungkulin. Ang admin ay may istasyon (Marilao o
     * Monumento) na ipinapakita kasama ng "Admin". Ang `name` pa rin
     * (hal. "admin") ang sinusulat sa "role" field sa Firestore, habang
     * ang adminStation ang ginagamit upang limitahan ang kontrol sa pila
     * sa nakatalagang terminal.
     */
    fun displayName(adminStation: String? = null): String =
        when {
            this != Admin -> name
            adminStation?.trim()?.lowercase() == STATION_MARILAO ->
                "Admin - Marilao"

            adminStation?.trim()?.lowercase() == STATION_MONUMENTO ->
                "Admin - Monumento"

            else -> "Admin"
        }
}
