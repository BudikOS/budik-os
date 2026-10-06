# SPDX-License-Identifier: GPL-3.0-only
# Budik OS performance profiles and RAM Plus (init .rc, no daemon), see budik_perf.rc.
# UI: apps/BudikControl (Settings page and Quick Settings tile)
PRODUCT_PACKAGES += BudikControl

PRODUCT_COPY_FILES += \
    vendor/budikos/perf/budik_perf.rc:$(TARGET_COPY_OUT_SYSTEM)/etc/init/budik_perf.rc \
    vendor/budikos/perf/budik_perf_ocean.rc:$(TARGET_COPY_OUT_SYSTEM)/etc/init/budik_perf_ocean.rc

# defaults until the user picks something: balanced, RAM Plus off
PRODUCT_SYSTEM_PROPERTIES += \
    persist.sys.budik.perf_profile=balanced \
    persist.sys.budik.ram_plus_gb=0
