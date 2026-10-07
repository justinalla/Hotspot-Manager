package com.example.scanner

import com.example.model.DeviceType

object MacVendorResolver {

    private val vendorPrefixes = mapOf(
        // Transsion (Infinix, Tecno, Itel)
        "70:70:8B" to Pair("Transsion (Infinix)", DeviceType.PHONE),
        "84:DB:AC" to Pair("Transsion (Tecno)", DeviceType.PHONE),
        "4C:2E:F3" to Pair("Transsion (Infinix)", DeviceType.PHONE),
        "B4:0B:44" to Pair("Infinix Mobility", DeviceType.PHONE),
        "28:6C:07" to Pair("Transsion / Infinix", DeviceType.PHONE),

        // Apple
        "3C:06:30" to Pair("Apple, Inc.", DeviceType.PHONE),
        "A4:83:E7" to Pair("Apple iPhone", DeviceType.PHONE),
        "BC:D1:1F" to Pair("Apple iPad", DeviceType.TABLET),
        "F0:18:98" to Pair("Apple MacBook", DeviceType.LAPTOP),
        "AC:BC:32" to Pair("Apple, Inc.", DeviceType.PHONE),
        "68:FB:7E" to Pair("Apple, Inc.", DeviceType.PHONE),

        // Samsung
        "34:41:5D" to Pair("Samsung Galaxy", DeviceType.PHONE),
        "78:40:E4" to Pair("Samsung Electronics", DeviceType.PHONE),
        "A8:7C:01" to Pair("Samsung Galaxy", DeviceType.PHONE),
        "50:01:D9" to Pair("Samsung Electronics", DeviceType.PHONE),
        "84:25:DB" to Pair("Samsung Smart TV", DeviceType.SMART_TV),
        "94:65:2D" to Pair("Samsung Galaxy Tab", DeviceType.TABLET),

        // Xiaomi / Redmi / Poco
        "64:09:80" to Pair("Xiaomi Communication", DeviceType.PHONE),
        "78:02:F8" to Pair("Xiaomi Redmi", DeviceType.PHONE),
        "50:64:2B" to Pair("Xiaomi Electronics", DeviceType.PHONE),
        "AC:C1:EE" to Pair("Poco / Xiaomi", DeviceType.PHONE),

        // Huawei / Honor
        "48:46:FB" to Pair("Huawei Device", DeviceType.PHONE),
        "00:1E:10" to Pair("Huawei Technologies", DeviceType.PHONE),
        "70:72:3C" to Pair("Honor Device", DeviceType.PHONE),

        // Oppo / Realme / OnePlus
        "2C:5B:B8" to Pair("OPPO Electronics", DeviceType.PHONE),
        "98:0D:2E" to Pair("Realme Mobile", DeviceType.PHONE),
        "AC:C0:48" to Pair("OnePlus Technology", DeviceType.PHONE),
        "6C:5A:B5" to Pair("Vivo Mobile", DeviceType.PHONE),

        // Google
        "3C:5A:B4" to Pair("Google Pixel", DeviceType.PHONE),
        "D8:6C:63" to Pair("Google Nest / Home", DeviceType.IOT),
        "54:60:09" to Pair("Google Chromecast", DeviceType.SMART_TV),

        // PCs / Laptops
        "B8:85:84" to Pair("Intel (PC Portable)", DeviceType.LAPTOP),
        "00:28:F8" to Pair("Dell Inc.", DeviceType.LAPTOP),
        "48:BA:4E" to Pair("HP Inc.", DeviceType.LAPTOP),
        "54:E1:AD" to Pair("Lenovo PC", DeviceType.LAPTOP),
        "00:1F:D0" to Pair("ASUSTek Computer", DeviceType.LAPTOP),
        "E4:54:E8" to Pair("Microsoft Surface", DeviceType.TABLET),

        // Consoles & TV
        "70:9E:29" to Pair("Sony PlayStation 5", DeviceType.CONSOLE),
        "60:38:0E" to Pair("Sony PlayStation 4", DeviceType.CONSOLE),
        "7C:BB:8A" to Pair("Microsoft Xbox", DeviceType.CONSOLE),
        "98:B6:E9" to Pair("Nintendo Switch", DeviceType.CONSOLE),
        "F8:0F:F9" to Pair("LG Electronics (TV)", DeviceType.SMART_TV),
        "68:37:E9" to Pair("Amazon Fire TV", DeviceType.SMART_TV),

        // IoT / Routers
        "B8:27:EB" to Pair("Raspberry Pi", DeviceType.IOT),
        "DC:A6:32" to Pair("Raspberry Pi Foundation", DeviceType.IOT),
        "50:C7:BF" to Pair("TP-Link Technologies", DeviceType.IOT),
        "2C:30:33" to Pair("Netgear", DeviceType.IOT)
    )

    fun resolve(mac: String): Pair<String, DeviceType> {
        val clean = mac.uppercase().replace("-", ":")
        if (clean.length >= 8) {
            val prefix = clean.substring(0, 8)
            vendorPrefixes[prefix]?.let { return it }
        }
        return Pair("Appareil Réseau", DeviceType.UNKNOWN)
    }

    fun inferTypeFromHostname(hostname: String, currentType: DeviceType): DeviceType {
        if (currentType != DeviceType.UNKNOWN) return currentType
        val lower = hostname.lowercase()
        return when {
            lower.contains("iphone") || lower.contains("galaxy") || lower.contains("redmi") ||
                    lower.contains("infinix") || lower.contains("pixel") || lower.contains("mobile") ||
                    lower.contains("android") -> DeviceType.PHONE
            lower.contains("ipad") || lower.contains("tab") -> DeviceType.TABLET
            lower.contains("macbook") || lower.contains("laptop") || lower.contains("pc") ||
                    lower.contains("desktop") || lower.contains("windows") || lower.contains("thinkpad") -> DeviceType.LAPTOP
            lower.contains("tv") || lower.contains("chromecast") || lower.contains("roku") -> DeviceType.SMART_TV
            lower.contains("playstation") || lower.contains("xbox") || lower.contains("switch") -> DeviceType.CONSOLE
            lower.contains("esp") || lower.contains("raspberry") || lower.contains("cam") -> DeviceType.IOT
            else -> DeviceType.UNKNOWN
        }
    }
}
