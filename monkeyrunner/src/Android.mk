#
# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_JAR_MANIFEST := ../etc/manifest.txt
LOCAL_JAVA_LIBRARIES := \
	chimpchat \
	ddmlib \
	jython \
	guavalib \
	jsilver \
	sdklib \
	hierarchyviewerlib \
	swt

LOCAL_JAVA_RESOURCE_DIRS := resources

LOCAL_MODULE := monkeyrunner

include $(BUILD_HOST_JAVA_LIBRARY)

include $(CLEAR_VARS)

MR_GEN_DOC_DIR := $(OUT_DOCS)/gen/guide/developing/tools
MONKEYRUNNER_GENERATED_DOC := $(MR_GEN_DOC_DIR)/monkeyrunner-api.jd

HELP_PY_PATH := $(TOP)/sdk/monkeyrunner/scripts/help.py

# Setup documentation generation so we can include the MonkeyRunner
# docs in the SDK.
$(MONKEYRUNNER_GENERATED_DOC): monkeyrunner
	mkdir -p $(MR_GEN_DOC_DIR)
	$(HOST_OUT)/bin/monkeyrunner $(HELP_PY_PATH) sdk-docs $<

ALL_GENERATED_DOCS += $(MONKEYRUNNER_GENERATED_DOC)
