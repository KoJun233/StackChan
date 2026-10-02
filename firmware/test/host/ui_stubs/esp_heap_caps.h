#pragma once
#include <stddef.h>
#include <stdlib.h>
#define MALLOC_CAP_INTERNAL 4U
#define MALLOC_CAP_SPIRAM 1U
#define MALLOC_CAP_8BIT 2U
static inline void *heap_caps_malloc(size_t size, unsigned caps) { (void)caps; return malloc(size); }
static inline void *heap_caps_calloc(size_t count, size_t size, unsigned caps) { (void)caps; return calloc(count, size); }
static inline size_t heap_caps_get_free_size(unsigned caps) { (void)caps; return 0; }
static inline size_t heap_caps_get_largest_free_block(unsigned caps) { (void)caps; return 0; }
