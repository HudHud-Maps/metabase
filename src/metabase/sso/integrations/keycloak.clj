;; Added by HudHud Maps, 2026-11-21: Keycloak sign-in for our own build of Metabase (free edition).
;; Licensed under the GNU Affero General Public License, as the rest of Metabase outside `enterprise/`.
;; Follows the free `metabase.sso.integrations.slack-connect`.
(ns metabase.sso.integrations.keycloak
  "Keycloak sign-in flow (OIDC authorization code, GET only).

   1. GET /auth/sso/keycloak redirects to Keycloak, with a signed state cookie.
   2. Keycloak redirects to GET /auth/sso/keycloak/callback?code=...&state=...
   3. Metabase checks state, exchanges the code, validates the ID token, then signs the person in."
  (:require
   [java-time.api :as t]
   [metabase.auth-identity.core :as auth-identity]
   [metabase.request.core :as request]
   [metabase.sso.core :as sso]
   [metabase.sso.keycloak.settings :as keycloak.settings]
   [metabase.sso.providers.keycloak]
   [metabase.sso.providers.slack-connect :as slack-connect.provider]
   [metabase.system.core :as system]
   [metabase.util.i18n :refer [tru]]
   [metabase.util.log :as log]
   [ring.util.response :as response]))

(set! *warn-on-reflection* true)

(defn- redirect-uri []
  (str (system/site-url) "/auth/sso/keycloak/callback"))

(defn- check-enabled! []
  (when-not (keycloak.settings/keycloak-auth-enabled)
    (throw (ex-info (tru "Keycloak sign-in is not configured.") {:status-code 400})))
  (when-not (system/site-url)
    (throw (ex-info (tru "Site URL is not set; Keycloak sign-in needs it for the callback address.")
                    {:status-code 500}))))

(defn sso-initiate
  "Redirect to Keycloak."
  [req]
  (check-enabled!)
  (let [redirect     (get-in req [:params :redirect])
        ;; the free Slack Connect provider's open-redirect check: only relative paths or our own host
        redirect-url (if redirect (slack-connect.provider/check-sso-redirect redirect) "/")
        auth-result  (auth-identity/authenticate :provider/keycloak
                                                 (assoc req :redirect-uri (redirect-uri)))]
    (if (= :redirect (:success? auth-result))
      (sso/wrap-oidc-redirect auth-result req :keycloak redirect-url {:browser-id (:browser-id req)})
      (throw (ex-info (or (:message auth-result) (tru "Could not start Keycloak sign-in."))
                      {:status-code 500})))))

(defn sso-callback
  "Finish the sign-in and set the session cookie."
  [req]
  (check-enabled!)
  (let [{:keys [code state]} (:params req)
        result (auth-identity/login! :provider/keycloak
                                     (assoc req
                                            :code code
                                            :state state
                                            :oidc-provider :keycloak
                                            :redirect-uri (redirect-uri)
                                            :device-info (request/device-info req)))]
    (if (:success? result)
      (let [base (-> (response/redirect (or (:redirect-url result) "/"))
                     (sso/clear-oidc-state-cookie))]
        (if-let [session (:session result)]
          (request/set-session-cookies req base session (t/zoned-date-time (t/zone-id "GMT")))
          base))
      (let [msg (or (:message result) (tru "Keycloak sign-in failed."))]
        (log/warnf "Keycloak sign-in refused: %s (%s)" msg (:error result))
        (throw (ex-info (str msg) {:status-code 401}))))))
