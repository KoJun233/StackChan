idf_component_register(
    SRCS "media003_eaf_probe.cpp"
    INCLUDE_DIRS "include"
    REQUIRES esp_lv_eaf_player lvgl
    PRIV_REQUIRES esp_timer heap
    EMBED_FILES "assets/media003-benchmark.eaf")
