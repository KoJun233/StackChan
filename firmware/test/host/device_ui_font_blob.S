    .section .rodata
    .balign 4
    .global _binary_device_cjk14_bin_start
    .global _binary_device_cjk14_bin_end
_binary_device_cjk14_bin_start:
    .incbin "main/fonts/device_cjk14.bin"
_binary_device_cjk14_bin_end:
    .balign 4
    .global _binary_device_menu18_bin_start
_binary_device_menu18_bin_start:
    .incbin "main/fonts/device_menu18.bin"
    .balign 4
    .global _binary_device_numbers32_bin_start
_binary_device_numbers32_bin_start:
    .incbin "main/fonts/device_numbers32.bin"
    .section .note.GNU-stack,"",@progbits
