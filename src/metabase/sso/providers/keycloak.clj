;; Added by HudHud Maps, 2026-11-21: Keycloak sign-in for our own build of Metabase (free edition).
;; Licensed under the GNU Affero General Public License, as the rest of Metabase outside `enterprise/`.
;; Built on the free `:provider/oidc`, the way the free `metabase.sso.providers.slack-connect` is.
(ns metabase.sso.providers.keycloak
  "Keycloak sign-in. Derives from the free base OIDC provider and adds:
   - only verified emails in the allowed domain;
   - accounts created at first sign-in;
   - at every sign-in, membership of the admin group (from the `groups` claim) sets the Administrators group."
  (:require
   [clojure.string :as str]
   [metabase.auth-identity.core :as auth-identity]
   [metabase.permissions.core :as perms]
   [metabase.sso.common :as sso.common]
   [metabase.sso.keycloak.settings :as keycloak.settings]
   [metabase.util.i18n :refer [tru]]
   [metabase.util.log :as log]
   [methodical.core :as methodical]))

(set! *warn-on-reflection* true)

;;; -------------------------------------------------- Provider Registration --------------------------------------------------

(auth-identity/derive! :provider/keycloak :provider/oidc)

(def provider-name
  "Provider name, stored in AuthIdentity and as the user's `sso_source`."
  "keycloak")

;;; -------------------------------------------------- Rules --------------------------------------------------

(defn email-allowed?
  "True when `email` is in the allowed domain (exact domain, case-insensitive)."
  [email]
  (let [domain (some-> (keycloak.settings/keycloak-auth-allowed-email-domain) str/trim str/lower-case)
        email  (some-> email str/trim str/lower-case)]
    (boolean (and (not (str/blank? domain))
                  (not (str/blank? email))
                  (str/ends-with? email (str "@" domain))
                  ;; exactly one @, so "a@evil.com@hudhud.sa"-style values never pass
                  (= 1 (count (filter #{\@} email)))))))

(defn email-verified?
  "Keycloak must say the email is verified. Unlike the base provider, a missing claim is refused."
  [claims]
  (let [v (:email_verified claims)]
    (or (true? v) (= "true" v))))

(defn admin?
  "True when the `groups` claim contains the admin group. Keycloak sends a flat list of names; a leading `/` (full
   group path) is accepted too."
  [claims]
  (let [admin-group (keycloak.settings/keycloak-auth-admin-group)
        groups      (:groups claims)]
    (boolean (and (not (str/blank? admin-group))
                  (sequential? groups)
                  (some #(= admin-group (str/replace (str %) #"^/" "")) groups)))))

(defn- oidc-config
  [request]
  (when (keycloak.settings/keycloak-auth-enabled)
    {:client-id     (keycloak.settings/keycloak-auth-client-id)
     :client-secret (keycloak.settings/keycloak-auth-client-secret)
     :issuer-uri    (str/replace (keycloak.settings/keycloak-auth-issuer-uri) #"/+$" "")
     :scopes        ["openid" "email" "profile"]
     :redirect-uri  (:redirect-uri request)}))

;;; -------------------------------------------------- Authentication --------------------------------------------------

(methodical/defmethod auth-identity/authenticate :provider/keycloak
  [provider request]
  (if-let [config (oidc-config request)]
    (let [result (next-method provider (assoc request :oidc-config config))]
      (if-not (and (true? (:success? result)) (:user-data result))
        result
        (let [claims (:claims result)
              email  (get-in result [:user-data :email])]
          (cond
            (not (email-verified? claims))
            {:success? false
             :error    :email-not-verified
             :message  (tru "Your email address is not verified in Keycloak.")}

            (not (email-allowed? email))
            {:success? false
             :error    :email-domain-not-allowed
             :message  (tru "Only company accounts can sign in to Metabase.")}

            :else
            (-> result
                (assoc-in [:user-data :email] (str/lower-case (str/trim email)))
                (assoc-in [:user-data :sso_source] :keycloak))))))
    {:success? false
     :error    :keycloak-not-configured
     :message  (tru "Keycloak sign-in is not configured.")}))

;;; -------------------------------------------------- Login --------------------------------------------------

(defn sync-admin!
  "Make the user's Administrators membership follow the admin group. Only the Administrators group is touched; other
   groups stay as they are."
  [user claims]
  (let [admin-group (perms/admin-group)]
    (sso.common/sync-group-memberships! user
                                        (if (admin? claims) [admin-group] [])
                                        [admin-group])))

(methodical/defmethod auth-identity/login! :after :provider/keycloak
  [_provider result]
  (when (and (true? (:success? result)) (:user result) (:claims result))
    (log/infof "Keycloak sign-in: user %s, admin %s" (:id (:user result)) (admin? (:claims result)))
    (sync-admin! (:user result) (:claims result)))
  result)
