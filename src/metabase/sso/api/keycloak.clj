;; Added by HudHud Maps, 2026-11-21: Keycloak sign-in for our own build of Metabase (free edition).
;; Licensed under the GNU Affero General Public License, as the rest of Metabase outside `enterprise/`.
(ns metabase.sso.api.keycloak
  "Routes for Keycloak sign-in: `/auth/sso/keycloak`."
  (:require
   [metabase.api.macros :as api.macros]
   [metabase.sso.integrations.keycloak :as keycloak]))

;; GET /auth/sso/keycloak
#_{:clj-kondo/ignore [:metabase/validate-defendpoint-has-response-schema]}
(api.macros/defendpoint :get "/"
  "Start Keycloak sign-in."
  [_route-params _query-params _body request]
  (keycloak/sso-initiate request))

;; GET /auth/sso/keycloak/callback
#_{:clj-kondo/ignore [:metabase/validate-defendpoint-has-response-schema]}
(api.macros/defendpoint :get "/callback"
  "Keycloak OIDC callback."
  [_route-params _query-params _body request]
  (keycloak/sso-callback request))

(def ^{:arglists '([request respond raise])} routes
  "`/auth/sso/keycloak` routes."
  (api.macros/ns-handler *ns*))
