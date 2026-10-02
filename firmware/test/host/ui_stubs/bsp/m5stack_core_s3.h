#pragma once
/* Rendering QA never accesses the board or its sensors. */
#include "driver/i2c_master.h"
#define BSP_I2C_NUM 0
i2c_master_bus_handle_t bsp_i2c_get_handle(void);
