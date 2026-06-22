package dev.thomasbuilds.spectre.scanner.wifi

import dev.thomasbuilds.spectre.model.WifiSecurity

internal object WifiCapabilities {
  fun parseSecurityTypes(capabilities: String?): Set<WifiSecurity> {
    val caps = capabilities.orEmpty()
    val types = linkedSetOf<WifiSecurity>()
    if ("WPA3" in caps || "SAE" in caps) types += WifiSecurity.WPA3
    if ("WPA2" in caps || "RSN" in caps) types += WifiSecurity.WPA2
    if ("WPA-" in caps || "WPA/" in caps || "WPA]" in caps) types += WifiSecurity.WPA
    if ("PSK" in caps) types += WifiSecurity.PSK
    if ("EAP" in caps) types += WifiSecurity.EAP
    if ("OWE" in caps) types += WifiSecurity.OWE
    if ("WEP" in caps) types += WifiSecurity.WEP
    if (types.isEmpty() && caps.contains("[ESS]")) types += WifiSecurity.OPEN
    return types
  }

  fun securityLabel(capabilities: String?): String {
    val labels = parseSecurityTypes(capabilities).map { it.label }
    return if (labels.isEmpty()) "—" else labels.joinToString(" / ")
  }

  fun ciphers(capabilities: String?): String {
    val caps = capabilities.orEmpty()
    val found =
      buildList {
        if ("GCMP-256" in caps) add("GCMP-256")
        if ("CCMP" in caps) add("CCMP")
        if ("TKIP" in caps) add("TKIP")
        if ("SMS4" in caps) add("SMS4")
        if ("WEP" in caps) add("WEP")
      }
    return if (found.isEmpty()) "None" else found.joinToString(" / ")
  }

  fun hasWps(capabilities: String?): Boolean = "WPS]" in capabilities.orEmpty()

  fun mfpStatus(capabilities: String?): String {
    val caps = capabilities.orEmpty()
    return when {
      "MFPR" in caps -> "Required"
      "MFPC" in caps -> "Capable"
      else -> "Not advertised"
    }
  }
}
