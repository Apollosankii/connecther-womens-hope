const fs = require('fs');
const path = require('path');
const {
  withAndroidManifest,
  withAppBuildGradle,
  withDangerousMod,
  withProjectBuildGradle,
} = require('@expo/config-plugins');

const GOOGLE_SERVICES_CLASSPATH =
  "classpath 'com.google.gms:google-services:4.4.2'";
const GOOGLE_SERVICES_PLUGIN = "apply plugin: 'com.google.gms.google-services'";

function ensureGoogleServicesClasspath(contents) {
  if (contents.includes('com.google.gms:google-services')) return contents;
  return contents.replace(
    /dependencies\s*\{/,
    (match) => `${match}\n    ${GOOGLE_SERVICES_CLASSPATH}`,
  );
}

function ensureGoogleServicesPlugin(contents) {
  if (contents.includes("com.google.gms.google-services")) return contents;
  return `${contents.trim()}\n${GOOGLE_SERVICES_PLUGIN}\n`;
}

/**
 * Ensures FCM works on Android: copy google-services.json + Gradle plugin + POST_NOTIFICATIONS.
 * Complements expo-notifications prebuild (required when android/ was generated before the plugin).
 */
function withAndroidFcm(config) {
  const googleServicesFile = config.android?.googleServicesFile;
  if (!googleServicesFile) return config;

  config = withDangerousMod(config, [
    'android',
    async (cfg) => {
      const projectRoot = cfg.modRequest.projectRoot;
      const src = path.resolve(projectRoot, googleServicesFile);
      const dest = path.join(projectRoot, 'android', 'app', 'google-services.json');
      if (!fs.existsSync(src)) {
        throw new Error(`[withAndroidFcm] Missing ${googleServicesFile}`);
      }
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.copyFileSync(src, dest);
      return cfg;
    },
  ]);

  config = withProjectBuildGradle(config, (cfg) => {
    cfg.modResults.contents = ensureGoogleServicesClasspath(cfg.modResults.contents);
    return cfg;
  });

  config = withAppBuildGradle(config, (cfg) => {
    cfg.modResults.contents = ensureGoogleServicesPlugin(cfg.modResults.contents);
    return cfg;
  });

  config = withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults;
    const usesPermissions = manifest.manifest['uses-permission'] ?? [];
    const hasPost = usesPermissions.some(
      (p) => p.$?.['android:name'] === 'android.permission.POST_NOTIFICATIONS',
    );
    if (!hasPost) {
      usesPermissions.push({
        $: { 'android:name': 'android.permission.POST_NOTIFICATIONS' },
      });
      manifest.manifest['uses-permission'] = usesPermissions;
    }
    return cfg;
  });

  return config;
}

module.exports = withAndroidFcm;
