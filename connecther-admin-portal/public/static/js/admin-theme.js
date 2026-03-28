/**
 * ConnectHer Admin — App Shell Controls
 * Theme persistence + sidebar show/hide
 */
(function() {
  'use strict';

  var THEME_STORAGE_KEY = 'connecther-admin-theme';
  var SIDEBAR_STORAGE_KEY = 'ch-sidebar-hidden';
  var THEMES = ['light', 'dark'];

  function getStoredTheme() {
    return localStorage.getItem(THEME_STORAGE_KEY) || 'light';
  }

  function setStoredTheme(theme) {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }

  function updateThemeToggleIcon(theme) {
    var icons = document.querySelectorAll('.theme-icon');
    icons.forEach(function(icon) {
      icon.textContent = theme === 'dark' ? '☀' : '🌙';
    });
  }

  function applyTheme(theme) {
    theme = THEMES.indexOf(theme) >= 0 ? theme : 'light';
    document.documentElement.setAttribute('data-theme', theme);
    setStoredTheme(theme);
    updateThemeToggleIcon(theme);
  }

  function cycleTheme() {
    var current = getStoredTheme();
    var next = current === 'light' ? 'dark' : 'light';
    applyTheme(next);
  }

  function restoreSidebarState() {
    var hidden = localStorage.getItem(SIDEBAR_STORAGE_KEY) === 'true';
    if (hidden) {
      document.body.classList.add('ch-sidebar-hidden');
    }
  }

  function init() {
    applyTheme(getStoredTheme());
    restoreSidebarState();

    document.querySelectorAll('#theme-toggle-btn, #theme-toggle-btn-mobile').forEach(function(btn) {
      if (btn) {
        btn.addEventListener('click', cycleTheme);
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
