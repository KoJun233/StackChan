idf_component_register(
    SRCS "media003_emote_probe.c"
    INCLUDE_DIRS "include"
    REQUIRES esp_emote_gfx
    PRIV_REQUIRES esp_timer heap)
