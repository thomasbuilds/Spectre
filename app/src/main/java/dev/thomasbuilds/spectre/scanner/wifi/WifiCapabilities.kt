package dev.thomasbuilds.spectre.scanner.wifi

import dev.thomasbuilds.spectre.model.WifiSecurity

internal object WifiCapabilities {
  // Parses per bracket group ("[RSN-PSK+SAE-CCMP-128]", "[WPA2-EAP/SHA1-CCMP]", ...) and keys the
  // WPA2 badge on the group's PSK/EAP AKMs: the WPA2/RSN prefix alone also fronts WPA3-only (SAE)
  // and OWE groups, which are not WPA2.
  fun parseSecurityTypes(capabilities: String?): Set<WifiSecurity> {
    val types = mutableSetOf<WifiSecurity>()
    var ess = false
    for (group in capabilities.orEmpty().split('[', ']')) {
      when {
        group.isBlank() -> Unit
        group == "ESS" -> ess = true
        group.startsWith("WPA-") -> {
          types += WifiSecurity.WPA
          if ("PSK" in group) types += WifiSecurity.PSK
          if ("EAP" in group) types += WifiSecurity.EAP
        }

        group.startsWith("WPA2") || group.startsWith("WPA3") || group.startsWith("RSN") -> {
          val suiteB = "SUITE_B_192" in group
          val wpa3 = "SAE" in group || suiteB || group.startsWith("WPA3")
          val owe = "OWE" in group
          val psk = "PSK" in group
          val eap = "EAP" in group
          if (wpa3) types += WifiSecurity.WPA3
          if (owe) types += WifiSecurity.OWE
          if (psk) {
            types += WifiSecurity.WPA2
            types += WifiSecurity.PSK
          }
          if (eap) {
            if (!suiteB) types += WifiSecurity.WPA2
            types += WifiSecurity.EAP
          }
          // A recognized RSN group with an unrecognized AKM still isn't open.
          if (!wpa3 && !owe && !psk && !eap) types += WifiSecurity.WPA2
        }

        group.startsWith("WEP") -> types += WifiSecurity.WEP
      }
    }
    if (types.isEmpty() && ess) types += WifiSecurity.OPEN
    return types.sortedBy(WifiSecurity::ordinal).toCollection(linkedSetOf())
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
