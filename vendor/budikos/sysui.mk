# SPDX-License-Identifier: GPL-3.0-only
# Budik OS SystemUI layer: performance on weak GPUs and the design overlay.
# Included from budikos.mk. Generic (GSI); every property and resource name is
# checked against the Android 17 source.

# --- SystemUI look: radius scale 6/12/20 dp, no blurred wallpaper
PRODUCT_PACKAGES += BudikSystemUIOverlay

# --- No cross-window blur (shade, recents/Launcher depth, power menu, dialogs).
# Gates CrossWindowBlurListeners.CROSS_WINDOW_BLUR_SUPPORTED, which SystemUI
# BlurUtils/WindowRootViewBlurRepository and Launcher3 BlurUtils check.
# Product props load last, so this also wins over a vendor that sets it to 1
# (e.g. Samsung). Blur falls back to the plain scrim, nothing breaks.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.surface_flinger.supports_background_blur=0

# --- First-boot wizard: Budik setup (apps/BudikSetup). Its Android.bp has
# overrides: ["Provision"], so AOSP Provision (handheld_system_ext.mk) is not
# installed: exactly one SETUP_WIZARD in the image. Disables itself when done.
PRODUCT_PACKAGES += BudikSetup

# --- First boot in Czech and Prague time (the wizard offers the rest).
# persist.* from build.prop is only the default until the user picks something
# (then /data/property wins). Not ro.product.locale: system and product
# build.prop already set it non-optionally from PRODUCT_LOCALES (en-US), a
# second value fails post_process_props; persist.sys.locale is read first
# anyway (AndroidRuntime::readLocale). Neither prop is set by the ocean vendor.
PRODUCT_SYSTEM_PROPERTIES += \
    persist.sys.locale=cs-CZ \
    persist.sys.timezone=Europe/Prague

# --- Budik island (phase A): black pill around the notch, ocean only
# (overlay is gated by ro.product.vendor.device=ocean, inert elsewhere).
PRODUCT_PACKAGES += BudikIslandOceanOverlay
