# ESP-SR built-in wake models

This directory contains only the built-in models exposed by the StackChan wake-word catalog.

- Source: `espressif/esp-sr` 2.4.6
- Upstream commit: `7ff63a7da40e15e502681be48c4d0e78475544a3`
- ESP-IDF component hash: `aa58d6a13a49600314a50a27240ccbd17e407f608216a3086d4868e0da2e8053`
- Upstream repository: <https://github.com/espressif/esp-sr>

Each model directory contains the unchanged upstream `_MODEL_INFO_`, `wn9_data`, and `wn9_index` files. `EspSrWakeWordModelCatalogTest` packages every exposed option and verifies the ESP-SR container structure and 1 MiB OTA partition budget.
