# ElementumService

A service that executes binaries for [Kodi](https://github.com/xbmc/xbmc)'s addon [Elementum](https://github.com/elgatito/plugin.video.elementum) on [Android](https://www.android.com/) without a [W^X violation](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission).

**Update:** The service should no longer be necessary for newer versions of [Elementum](https://github.com/elgatito/plugin.video.elementum) as they fallback to [loading](https://en.wikipedia.org/wiki/Dynamic_loading) binaries in the same process if they cannot start a new one. This project will be maintained in case something else comes up.

## Download

Use source [https://StefanIlchev.github.io/ElementumService](https://StefanIlchev.github.io/ElementumService).

## Install

Install an `.apk` with an [Application Binary Interface](https://en.wikipedia.org/wiki/Application_binary_interface) (ABI) that your device supports or the bigger universal one.

Add [Elementum](https://github.com/elgatito/plugin.video.elementum) to [Kodi](https://github.com/xbmc/xbmc) from the `.android_client.zip`. When the addon completes installation, it will start the service app which may ask you for some [Android permissions](https://support.google.com/googleplay/answer/6270602).

On [Android 11](https://developer.android.com/about/versions/11/privacy/storage#all-files-access) and up the service app may ask you to allow it to manage all files or try to open its settings if your device [does not let it](https://issuetracker.google.com/issues/71327396#comment5). If nothing else works the service app will show you the following command that will allow it to manage all files which you might have to execute for [Elementum](https://github.com/elgatito/plugin.video.elementum) to work properly:

```bat
adb shell appops set service.elementum.android MANAGE_EXTERNAL_STORAGE allow
```

## Auto-update

Reinstall the addon from the added `Elementum Service Repository` and [Kodi](https://github.com/xbmc/xbmc) should keep it up to date. If you do not see the repository as an option check if it is not disabled.

When updating itself the service app might ask you to allow it to install apps and to confirm the update. After an update the service [will not be started automatically](https://developer.android.com/guide/components/activities/background-starts). A start can be triggered by interacting with the addon which will try to start the service if it is not responding.

The service app will not work if it is a different version from the addon and will show a message like:

`<service-version> ≠ <client-version>`

## Build

Prepare an [Elementum](https://github.com/elgatito/plugin.video.elementum) addon `.zip` at `path/to/plugin.video.elementum-<version>.zip` containing [Android](https://www.android.com/) binaries for your device either by [building the Elementum project](https://github.com/elgatito/plugin.video.elementum#build) or by downloading it from a [release's](https://github.com/elgatito/plugin.video.elementum/releases) assets.

Assuming you already have [Android Studio](https://developer.android.com/studio) setup, execute the following command in a terminal:

```bat
gradlew clean assembleRelease zipAndroidClient -p ElementumService -Delementum.addon.zip="path/to/plugin.video.elementum-<version>.zip"
```

and when it completes successfully look for the following files in the newly created `ElementumService/build` folder:

1. `plugin.video.elementum-<version>.android_client.zip` found directly in the `ElementumService/build` folder is the addon `.zip` you provided with its binaries removed and scripts modified to work with the service app;

2. `ElementumService-<ABI>-release-<version>.apk` found in the `ElementumService/build/outputs/apk/release` subfolder are one or more `.apk` files containing the binaries from the addon `.zip` you provided.

## Notes

* From now on when [Elementum](https://github.com/elgatito/plugin.video.elementum) starts its daemon it will start the service app and when it stops it will stop the service app.

* You will no longer be able to use paths to [Kodi](https://github.com/xbmc/xbmc)'s data folder as they will be translated to the service app's data folder.

* The output from the binaries will no longer appear in [Kodi's log](https://kodi.wiki/view/Log_file) and will go to [Android's log](https://developer.android.com/studio/command-line/logcat) instead.
