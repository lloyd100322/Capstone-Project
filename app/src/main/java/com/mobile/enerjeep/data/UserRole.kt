package com.mobile.enerjeep.data


enum class UserRole {
    Superadmin,
    Admin,
    Driver;

    companion object {
        // Mga istasyon ng admin - ito ang pagpipilian sa "Tungkulin" na
        // "Admin - Marilao" at "Admin - Monumento" (CreateAccountScreen).
        const val STATION_MARILAO = "marilao"
        const val STATION_MONUMENTO = "monumento"
    }

    /**
     * Display label ng tungkulin. Ang admin ay may istasyon (Marilao o
     * Monumento) na ipinapakita kasama ng "Admin". Ito ay PANG-DISPLAY
     * lang - ang `name` pa rin (hal. "admin") ang sinusulat sa "role"
     * field sa Firestore, kaya hindi nababago ang login at permiso.
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
