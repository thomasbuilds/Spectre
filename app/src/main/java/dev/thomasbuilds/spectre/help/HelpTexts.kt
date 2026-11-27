package dev.thomasbuilds.spectre.help

internal const val CELLULAR_HELP =
  """5G NSA networks anchor your phone to a 4G cell and add a 5G carrier on top for speed. If your phone lists the 5G carrier as a separate cell, it appears as its own 5G tower. Otherwise the strength shown for your connection is the anchor's, not the 5G signal actually carrying your data.

You'll normally see only your own carrier's towers. Your phone measures the frequencies your SIM's network points it to, plus any roaming partners, so cells from other carriers rarely appear.

The signal strength shown is an estimate. For 4G, 5G, and 3G, a phone's main reading is a smaller reference measurement rather than a cell's total power. Spectre converts it into the full strength you see, using the phone's other readings where possible and an assumed offset otherwise. The estimate can read high on lightly loaded 4G and 5G cells. Expand a tower to see the raw measurement (RSRP, SS-RSRP, or RSCP). 2G reports total power, but Android caps it at -51 dBm, so -51 means that or stronger."""

internal const val WIFI_THROTTLE_HELP =
  """Android lets a foreground app scan Wi-Fi 4 times every 2 minutes, so Spectre scans every 31 seconds. To scan every 5 seconds, enable Developer options in your phone's settings and turn off "Wi-Fi scan throttling" under Networking."""

internal const val LOCAL_NETWORK_HELP =
  "Scans the local network you're connected to and lists the devices it finds, with the open ports and services on each. It combines direct port probing with mDNS and UPnP discovery, so it also catches devices that announce themselves, such as AirPlay, Chromecast, printers, and media servers."
