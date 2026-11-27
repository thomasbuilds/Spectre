package dev.thomasbuilds.spectre.wifi

import java.util.EnumSet

private val CIPHERS = listOf("GCMP-256", "GCMP-128", "CCMP", "TKIP", "SMS4", "WEP")

internal class WifiCapabilities(
  val security: Set<WifiSecurity>,
  val ciphers: List<String>,
  val hasWps: Boolean,
  val managementFrameProtection: ManagementFrameProtection
)

internal fun parseCapabilities(capabilities: String): WifiCapabilities {
  val groups = capabilities.split('[', ']').filter(String::isNotEmpty)
  val mfpRequired = "MFPR" in capabilities
  val mfpCapable = "MFPC" in capabilities
  val security = EnumSet.noneOf(WifiSecurity::class.java)
  for (group in groups) {
    when {
      group.startsWith("WPA-") -> {
        security += WifiSecurity.WPA
        if ("PSK" in group) security += WifiSecurity.PSK
        if ("EAP" in group) security += WifiSecurity.EAP
      }

      group.startsWith("RSN") || group.startsWith("WPA2") || group.startsWith("WPA3") -> {
        security += rsnSecurity(group, mfpRequired, mfpCapable)
      }

      group.startsWith("WAPI") -> {
        security += WifiSecurity.WAPI
      }

      group.startsWith("WEP") -> {
        security += WifiSecurity.WEP
      }
    }
  }
  if ("ESS" in groups && (security.isEmpty() || "OWE_TRANSITION" in capabilities)) security += WifiSecurity.OPEN
  return WifiCapabilities(
    security = security,
    ciphers =
      CIPHERS.filter { it in capabilities } +
        listOfNotNull("Unknown".takeIf { groups.any { "?" in it.substringAfterLast('-').split('+') } }),
    hasWps = "WPS]" in capabilities,
    managementFrameProtection =
      when {
        mfpRequired -> ManagementFrameProtection.REQUIRED
        mfpCapable -> ManagementFrameProtection.CAPABLE
        else -> ManagementFrameProtection.NONE
      }
  )
}

private fun rsnSecurity(
  group: String,
  mfpRequired: Boolean,
  mfpCapable: Boolean
): Set<WifiSecurity> =
  buildSet {
    val suiteB = "SUITE_B_192" in group
    val sha1 = "EAP/SHA1" in group
    val wpa3EnterpriseOnly = "EAP/SHA256" in group && !sha1 && mfpRequired
    val wpa3EnterpriseTransition = "EAP/SHA256" in group && sha1 && !mfpRequired && mfpCapable
    if ("SAE" in group || suiteB || group.startsWith("WPA3") || wpa3EnterpriseOnly || wpa3EnterpriseTransition) add(WifiSecurity.WPA3)
    if ("OWE" in group) add(WifiSecurity.OWE)
    if ("PSK" in group) addAll(listOf(WifiSecurity.WPA2, WifiSecurity.PSK))
    if ("EAP" in group) {
      add(WifiSecurity.EAP)
      if (!suiteB && !wpa3EnterpriseOnly) add(WifiSecurity.WPA2)
    }
    if (isEmpty()) add(WifiSecurity.WPA2)
  }
