/**
 * Prebuild fixes for local Android Gradle runs (Expo `run:android`):
 * 1. `org.gradle.java.home` in `gradle.properties` when `JAVA_HOME` is missing or invalid.
 * 2. `sdk.dir` in `local.properties` when `ANDROID_HOME` / `ANDROID_SDK_ROOT` is unset (common on Windows).
 *
 * Overrides:
 * - `EXPO_ANDROID_JAVA_HOME` — JDK root (contains `bin/java` or `bin/java.exe`).
 * - `EXPO_ANDROID_SDK_ROOT` — Android SDK root (contains `platform-tools/adb`).
 */
const fs = require('fs');
const path = require('path');
const { AndroidConfig, withDangerousMod, withGradleProperties } = require('expo/config-plugins');

function hasJavaBinary(home) {
  if (!home) return false;
  const javaName = process.platform === 'win32' ? 'java.exe' : 'java';
  return fs.existsSync(path.join(home, 'bin', javaName));
}

function resolveJavaHome() {
  const fromEnv = process.env.EXPO_ANDROID_JAVA_HOME?.trim();
  if (fromEnv && hasJavaBinary(fromEnv)) return fromEnv;

  const javaHome = process.env.JAVA_HOME?.trim();
  if (javaHome && hasJavaBinary(javaHome)) return javaHome;

  if (process.platform === 'win32') {
    const studioJbr = 'C:\\Program Files\\Android\\Android Studio\\jbr';
    if (hasJavaBinary(studioJbr)) return studioJbr;
  }
  if (process.platform === 'darwin') {
    const studioJbr = '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
    if (hasJavaBinary(studioJbr)) return studioJbr;
  }
  return null;
}

function hasAndroidSdk(sdkRoot) {
  if (!sdkRoot) return false;
  const adb = process.platform === 'win32' ? 'adb.exe' : 'adb';
  return fs.existsSync(path.join(sdkRoot, 'platform-tools', adb));
}

function resolveAndroidSdk() {
  const fromEnv = process.env.EXPO_ANDROID_SDK_ROOT?.trim();
  if (fromEnv && hasAndroidSdk(fromEnv)) return fromEnv;

  const androidHome = process.env.ANDROID_HOME?.trim() || process.env.ANDROID_SDK_ROOT?.trim();
  if (androidHome && hasAndroidSdk(androidHome)) return androidHome;

  if (process.platform === 'win32') {
    const d = path.join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk');
    if (hasAndroidSdk(d)) return d;
  }
  if (process.platform === 'darwin') {
    const d = path.join(process.env.HOME || '', 'Library', 'Android', 'sdk');
    if (hasAndroidSdk(d)) return d;
  }
  return null;
}

function mergeSdkDirLine(contents, sdkRoot) {
  const line = `sdk.dir=${sdkRoot.replace(/\\/g, '/')}`;
  const trimmed = (contents || '').trimEnd();
  if (!trimmed) return `${line}\n`;
  if (/^sdk\.dir=/m.test(trimmed)) {
    return `${trimmed.replace(/^sdk\.dir=.*$/m, line)}\n`;
  }
  return `${trimmed}\n${line}\n`;
}

/** @type {import('expo/config-plugins').ConfigPlugin} */
module.exports = function withAndroidGradleTooling(config) {
  let next = withDangerousMod(config, [
    'android',
    async (cfg) => {
      const sdk = resolveAndroidSdk();
      if (!sdk) {
        // eslint-disable-next-line no-console
        console.warn(
          '[withAndroidGradleTooling] Android SDK not found; set ANDROID_HOME or EXPO_ANDROID_SDK_ROOT, or install the SDK under the default path.',
        );
        return cfg;
      }
      const androidRoot = cfg.modRequest.platformProjectRoot;
      const localPropsPath = path.join(androidRoot, 'local.properties');
      let existing = '';
      try {
        existing = await fs.promises.readFile(localPropsPath, 'utf8');
      } catch {
        existing = '';
      }
      await fs.promises.writeFile(localPropsPath, mergeSdkDirLine(existing, sdk), 'utf8');
      return cfg;
    },
  ]);

  next = withGradleProperties(next, (cfg) => {
    const home = resolveJavaHome();
    if (home) {
      const normalized = home.replace(/\\/g, '/');
      cfg.modResults = AndroidConfig.BuildProperties.updateAndroidBuildProperty(
        cfg.modResults,
        'org.gradle.java.home',
        normalized,
      );
    }
    return cfg;
  });

  return next;
};
