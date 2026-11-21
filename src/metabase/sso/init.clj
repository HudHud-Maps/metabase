;; Modified by HudHud Maps, 2026-11-21: loads the Keycloak provider and settings (Keycloak sign-in, AGPL; see metabase.sso.providers.keycloak).
(ns metabase.sso.init
  (:require
   [metabase.sso.ldap.settings]
   [metabase.sso.keycloak.settings]
   [metabase.sso.providers.google]
   [metabase.sso.providers.keycloak]
   [metabase.sso.providers.ldap]
   [metabase.sso.providers.oidc]
   [metabase.sso.providers.slack-connect]
   [metabase.sso.settings]))
