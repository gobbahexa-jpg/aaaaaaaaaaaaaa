package com.example.core

data class CountryDialCode(
    val isoCode: String,
    val name: String,
    val dialCode: String,
    val flagEmoji: String,
    val exampleLocal: String
)

object PhoneNumberHelper {

    val countries: List<CountryDialCode> = listOf(
        CountryDialCode("LK", "Sri Lanka", "+94", "🇱🇰", "77 123 4567"),
        CountryDialCode("US", "United States", "+1", "🇺🇸", "415 555 0142"),
        CountryDialCode("GB", "United Kingdom", "+44", "🇬🇧", "7700 900123"),
        CountryDialCode("IN", "India", "+91", "🇮🇳", "98765 43210"),
        CountryDialCode("SG", "Singapore", "+65", "🇸🇬", "8123 4567"),
        CountryDialCode("AU", "Australia", "+61", "🇦🇺", "412 345 678"),
        CountryDialCode("AE", "United Arab Emirates", "+971", "🇦🇪", "50 123 4567"),
        CountryDialCode("CA", "Canada", "+1", "🇨🇦", "647 555 0198"),
        CountryDialCode("DE", "Germany", "+49", "🇩🇪", "1512 3456789"),
        CountryDialCode("FR", "France", "+33", "🇫🇷", "6 12 34 56 78"),
        CountryDialCode("JP", "Japan", "+81", "🇯🇵", "90 1234 5678"),
        CountryDialCode("KR", "South Korea", "+82", "🇰🇷", "10 1234 5678"),
        CountryDialCode("MY", "Malaysia", "+60", "🇲🇾", "12 345 6789"),
        CountryDialCode("PK", "Pakistan", "+92", "🇵🇰", "300 1234567"),
        CountryDialCode("BD", "Bangladesh", "+880", "🇧🇩", "1712 345678"),
        CountryDialCode("ID", "Indonesia", "+62", "🇮🇩", "812 3456 7890"),
        CountryDialCode("NL", "Netherlands", "+31", "🇳🇱", "6 12345678"),
        CountryDialCode("BR", "Brazil", "+55", "🇧🇷", "11 91234 5678"),
        CountryDialCode("ZA", "South Africa", "+27", "🇿🇦", "82 123 4567"),
        CountryDialCode("SA", "Saudi Arabia", "+966", "🇸🇦", "50 123 4567")
    )

    val defaultCountry: CountryDialCode = countries.first() // Sri Lanka (+94)

    private val e164Regex = Regex("^\\+[1-9][0-9]{6,14}$")

    fun findCountry(isoOrDial: String): CountryDialCode {
        val clean = isoOrDial.trim()
        return countries.firstOrNull {
            it.isoCode.equals(clean, ignoreCase = true) || it.dialCode == clean
        } ?: defaultCountry
    }

    fun searchCountries(query: String): List<CountryDialCode> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return countries
        return countries.filter {
            it.name.lowercase().contains(q) ||
                it.isoCode.lowercase().contains(q) ||
                it.dialCode.contains(q)
        }
    }

    fun normalizeToE164(country: CountryDialCode, rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val cleaned = trimmed.replace(Regex("[\\s\\-()\\.]"), "")
        val candidate = if (cleaned.startsWith("+")) {
            "+" + cleaned.drop(1).filter { it.isDigit() }
        } else {
            val digitsOnly = cleaned.filter { it.isDigit() }
            if (digitsOnly.isEmpty()) return null
            val dialDigits = country.dialCode.removePrefix("+")
            when {
                digitsOnly.startsWith("00") && digitsOnly.length > 4 -> "+" + digitsOnly.drop(2)
                digitsOnly.startsWith("0") -> country.dialCode + digitsOnly.drop(1)
                digitsOnly.startsWith(dialDigits) && digitsOnly.length >= dialDigits.length + 7 ->
                    "+$digitsOnly"
                else -> country.dialCode + digitsOnly
            }
        }
        return candidate.takeIf { isValidE164(it) }
    }

    fun isValidE164(value: String): Boolean = e164Regex.matches(value.trim())

    fun maskPhone(e164: String): String {
        val clean = e164.trim()
        if (clean.length <= 6) return "***"
        val prefix = clean.take(4)
        val suffix = clean.takeLast(4)
        return "$prefix••••$suffix"
    }

    fun formatReadable(e164: String): String {
        val clean = e164.trim()
        if (!isValidE164(clean)) return clean
        return if (clean.startsWith("+94") && clean.length == 12) {
            "+94 ${clean.substring(3, 5)} ${clean.substring(5, 8)} ${clean.substring(8)}"
        } else {
            clean
        }
    }
}
