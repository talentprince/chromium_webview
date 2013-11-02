# Updates this project with the Chrome build files.
# This script assumes the Chrome build VM is up at crbuild.local
# To Base folder
cd ../
# Clean up.
sudo rm -r assets/*
sudo rm -r libs/*
sudo rm -r src/com/googlecode
sudo rm -r src/org/chromium

# ContentShell core -- use this if android_webview doesn't work out.
#scp chenchen@ubuntu.local:chromium/src/out/Release/content_shell/assets/* \
#    assets/
#scp -r chenchen@ubuntu.local:chromium/src/out/Release/content_shell_apk/libs/* \
#    libs
#scp -r chenchen@ubuntu.local:chromium/src/content/shell/android/java/res/* res
#scp -r chenchen@ubuntu.local:chromium/src/content/shell/android/java/src/* src
#scp -r chenchen@ubuntu.local:chromium/src/content/shell_apk/android/java/res/* res

# android_webview
scp chenchen@ubuntu.local:chromium/src/out/Release/android_webview_apk/assets/*.pak \
    assets
scp -r chenchen@ubuntu.local:chromium/src/out/Release/android_webview_apk/libs/* \
    libs
rm libs/**/gdbserver
scp -r chenchen@ubuntu.local:chromium/src/android_webview/java/src/* src/

## Dependencies inferred from android_webview/Android.mk

# Resources.
scp -r chenchen@ubuntu.local:chromium/src/content/public/android/java/resource_map/* src/
scp -r chenchen@ubuntu.local:chromium/src/ui/android/java/resource_map/* src/

# ContentView dependencies.
scp -r chenchen@ubuntu.local:chromium/src/base/android/java/src/* src/
scp -r chenchen@ubuntu.local:chromium/src/content/public/android/java/src/* src/
scp -r chenchen@ubuntu.local:chromium/src/media/base/android/java/src/* src/
scp -r chenchen@ubuntu.local:chromium/src/net/android/java/src/* src/
scp -r chenchen@ubuntu.local:chromium/src/ui/android/java/src/* src/
scp -r chenchen@ubuntu.local:chromium/src/third_party/eyesfree/src/android/java/src/* src/

# Strip a ContentView file that's not supposed to be here.
rm src/org/chromium/content/common/common.aidl

# Get rid of the .git directory in eyesfree.
sudo rm -r src/com/googlecode/eyesfree/braille/.git

# Remove ReousrceID.java
rm src/org/chromium/chrome/browser/ResourceId.java

# Browser components.
scp -r chenchen@ubuntu.local:chromium/src/components/web_contents_delegate_android/android/java/src/* src/
scp -r chenchen@ubuntu.local:chromium/src/components/navigation_interception/android/java/src/* src/

# Generated files.
scp -r chenchen@ubuntu.local:chromium/src/out/Release/gen/templates/* src/

# JARs.
scp -r chenchen@ubuntu.local:chromium/src/out/Release/lib.java/guava_javalib.jar libs/
scp -r chenchen@ubuntu.local:chromium/src/out/Release/lib.java/jsr_305_javalib.jar libs/

# android_webview generated sources. Must come after all the other sources.
scp -r chenchen@ubuntu.local:chromium/src/android_webview/java/generated_src/* src/

# copy support v4
cp android-support-v4.jar ./libs
