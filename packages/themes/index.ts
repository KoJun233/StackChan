export const lightTheme = {
  // shadcn
  '--background': '0.97547 0.00453 258.32453',
  '--foreground': '0.32244 0.04212 258.30280',
  '--card': '1.00000 0.00000 0.00000',
  '--card-foreground': '0.32244 0.04212 258.30280',
  '--popover': '1.00000 0.00000 0.00000',
  '--popover-foreground': '0.32244 0.04212 258.30280',
  '--primary': '0.54601 0.10292 290.09162',
  '--primary-foreground': '1.00000 0.00000 0.00000',
  '--secondary': '0.93409 0.01769 296.62249',
  '--secondary-foreground': '0.42436 0.07894 294.28241',
  '--muted': '0.95429 0.00744 260.73154',
  '--muted-foreground': '0.51502 0.03306 261.37392',
  '--accent': '0.93552 0.01873 171.49180',
  '--accent-foreground': '0.39385 0.03895 170.54559',
  '--destructive': '0.577 0.245 27.325', // oklch(0.577 0.245 27.325)
  '--border': '0.91160 0.01338 262.37735',
  '--input': '0.79558 0.02523 259.82082',
  '--ring': '0.54601 0.10292 290.09162',
  // 主要区域
  '--g-main-area-bg': 'oklch(var(--background))',
  // 头部
  '--g-header-bg': 'oklch(var(--background))',
  '--g-header-color': 'oklch(var(--foreground))',
  '--g-header-menu-color': 'oklch(var(--accent-foreground))',
  '--g-header-menu-hover-bg': 'oklch(var(--accent))',
  '--g-header-menu-hover-color': 'oklch(var(--accent-foreground))',
  '--g-header-menu-active-bg': 'oklch(var(--primary))',
  '--g-header-menu-active-color': 'oklch(var(--primary-foreground))',
  // 主导航
  '--g-main-sidebar-bg': 'oklch(var(--background))',
  '--g-main-sidebar-menu-color': 'oklch(var(--accent-foreground))',
  '--g-main-sidebar-menu-hover-bg': 'oklch(var(--accent))',
  '--g-main-sidebar-menu-hover-color': 'oklch(var(--accent-foreground))',
  '--g-main-sidebar-menu-active-bg': 'oklch(var(--primary))',
  '--g-main-sidebar-menu-active-color': 'oklch(var(--primary-foreground))',
  // 次导航
  '--g-sub-sidebar-bg': 'oklch(var(--background))',
  '--g-sub-sidebar-menu-color': 'oklch(var(--accent-foreground))',
  '--g-sub-sidebar-menu-hover-bg': 'oklch(var(--accent))',
  '--g-sub-sidebar-menu-hover-color': 'oklch(var(--accent-foreground))',
  '--g-sub-sidebar-menu-active-bg': 'oklch(var(--primary))',
  '--g-sub-sidebar-menu-active-color': 'oklch(var(--primary-foreground))',
  // 标签栏
  '--g-tabbar-bg': 'oklch(var(--background))',
  '--g-tabbar-tab-color': 'oklch(var(--muted-foreground))',
  '--g-tabbar-tab-hover-bg': 'oklch(var(--accent) / 50%)',
  '--g-tabbar-tab-hover-color': 'oklch(var(--foreground))',
  '--g-tabbar-tab-active-bg': 'oklch(var(--accent))',
  '--g-tabbar-tab-active-color': 'oklch(var(--foreground))',
  // 工具栏
  '--g-toolbar-bg': 'oklch(var(--background))',
} as const

export const darkTheme = {
  // shadcn
  '--background': '0.26066 0.02675 253.33252',
  '--foreground': '0.95955 0.00796 253.85338',
  '--card': '0.31639 0.03595 253.67000',
  '--card-foreground': '0.95955 0.00796 253.85338',
  '--popover': '0.31639 0.03595 253.67000',
  '--popover-foreground': '0.95955 0.00796 253.85338',
  '--primary': '0.76818 0.08460 296.01252',
  '--primary-foreground': '0.25770 0.03393 296.70610',
  '--secondary': '0.33950 0.04720 293.93660',
  '--secondary-foreground': '0.92149 0.02915 300.98365',
  '--muted': '0.35097 0.03724 253.35225',
  '--muted-foreground': '0.79367 0.02810 255.11653',
  '--accent': '0.38192 0.03447 169.99256',
  '--accent-foreground': '0.93077 0.03012 167.43222',
  '--destructive': '0.704 0.191 22.216', // oklch(0.704 0.191 22.216)
  '--border': '0.42677 0.04197 256.44119',
  '--input': '0.59857 0.03994 257.41391',
  '--ring': '0.76818 0.08460 296.01252',
  // 主要区域
  '--g-main-area-bg': 'oklch(var(--background))',
  // 头部
  '--g-header-bg': 'oklch(var(--background))',
  '--g-header-color': 'oklch(var(--foreground))',
  '--g-header-menu-color': 'oklch(var(--muted-foreground))',
  '--g-header-menu-hover-bg': 'oklch(var(--muted))',
  '--g-header-menu-hover-color': 'oklch(var(--muted-foreground))',
  '--g-header-menu-active-bg': 'oklch(var(--accent))',
  '--g-header-menu-active-color': 'oklch(var(--accent-foreground))',
  // 主导航
  '--g-main-sidebar-bg': 'oklch(var(--background))',
  '--g-main-sidebar-menu-color': 'oklch(var(--muted-foreground))',
  '--g-main-sidebar-menu-hover-bg': 'oklch(var(--muted))',
  '--g-main-sidebar-menu-hover-color': 'oklch(var(--muted-foreground))',
  '--g-main-sidebar-menu-active-bg': 'oklch(var(--accent))',
  '--g-main-sidebar-menu-active-color': 'oklch(var(--accent-foreground))',
  // 次导航
  '--g-sub-sidebar-bg': 'oklch(var(--background))',
  '--g-sub-sidebar-menu-color': 'oklch(var(--muted-foreground))',
  '--g-sub-sidebar-menu-hover-bg': 'oklch(var(--muted))',
  '--g-sub-sidebar-menu-hover-color': 'oklch(var(--muted-foreground))',
  '--g-sub-sidebar-menu-active-bg': 'oklch(var(--accent))',
  '--g-sub-sidebar-menu-active-color': 'oklch(var(--accent-foreground))',
  // 标签栏
  '--g-tabbar-bg': 'oklch(var(--background))',
  '--g-tabbar-tab-color': 'oklch(var(--muted-foreground))',
  '--g-tabbar-tab-hover-bg': 'oklch(var(--accent) / 50%)',
  '--g-tabbar-tab-hover-color': 'oklch(var(--foreground))',
  '--g-tabbar-tab-active-bg': 'oklch(var(--accent))',
  '--g-tabbar-tab-active-color': 'oklch(var(--foreground))',
  // 工具栏
  '--g-toolbar-bg': 'oklch(var(--background))',
} as const
