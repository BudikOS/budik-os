# SPDX-License-Identifier: GPL-3.0-only
LOCAL_PATH := $(call my-dir)

# Debloat: overriding a package keeps it out of the image without touching
# the makefiles that add it. Names must match the module names exactly.
include $(CLEAR_VARS)
LOCAL_MODULE := BudikRemovePackages
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_OVERRIDES_PACKAGES := \
    talkback \
    Seedvault \
    SpeechServices \
    ManagedProvisioning
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := /dev/null
include $(BUILD_PREBUILT)
