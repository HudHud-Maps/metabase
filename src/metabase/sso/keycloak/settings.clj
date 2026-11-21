;; Added by HudHud Maps, 2026-11-21: Keycloak sign-in for our own build of Metabase (free edition).
;; Licensed under the GNU Affero General Public License, as the rest of Metabase outside `enterprise/`.
;; Written from the free OIDC code in `metabase.sso.oidc`; nothing here comes from `enterprise/`.
(ns metabase.sso.keycloak.settings
  "Settings for Keycloak sign-in. They are read from the environment only (`MB_KEYCLOAK_AUTH_*`), so they can't be
   changed from the admin pages or the API."
  (:require
   [clojure.string :as str]
   [metabase.settings.core :as setting :refer [defsetting]]
   [metabase.util.i18n :refer [deferred-tru]]))

(set! *warn-on-reflection* true)

(defsetting keycloak-auth-issuer-uri
  (deferred-tru "Keycloak realm URL (OIDC issuer), for example https://keycloak.example.com/realms/data")
  :type       :string
  :encryption :no
  :visibility :internal
  :export?    false
  :setter     :none
  :audit      :never)

(defsetting keycloak-auth-client-id
  (deferred-tru "Keycloak client ID for Metabase.")
  :type       :string
  :encryption :no
  :visibility :internal
  :export?    false
  :setter     :none
  :audit      :never)

(defsetting keycloak-auth-client-secret
  (deferred-tru "Keycloak client secret for Metabase.")
  :type       :string
  :visibility :internal
  :sensitive? true
  :encryption :when-encryption-key-set
  :export?    false
  :setter     :none
  :audit      :never)

(defsetting keycloak-auth-allowed-email-domain
  (deferred-tru "Only people whose verified email is in this domain may sign in with Keycloak.")
  :type       :string
  :encryption :no
  :visibility :internal
  :export?    false
  :setter     :none
  :default    "hudhud.sa"
  :audit      :never)

(defsetting keycloak-auth-admin-group
  (deferred-tru "Members of this Keycloak group (from the `groups` claim) are Metabase Administrators.")
  :type       :string
  :encryption :no
  :visibility :internal
  :export?    false
  :setter     :none
  :default    "data-admins"
  :audit      :never)

(defsetting keycloak-auth-enabled
  (deferred-tru "Is Keycloak sign-in configured?")
  :type       :boolean
  :visibility :public
  :export?    false
  :setter     :none
  :audit      :never
  :getter     (fn []
                (every? (comp not str/blank?)
                        [(keycloak-auth-issuer-uri) (keycloak-auth-client-id) (keycloak-auth-client-secret)])))

(defsetting hudhud-build-source-url
  (deferred-tru "Where the source of this build is (AGPL section 13). Shown on the sign-in page.")
  :type       :string
  :encryption :no
  :visibility :public
  :export?    false
  :setter     :none
  :audit      :never)
