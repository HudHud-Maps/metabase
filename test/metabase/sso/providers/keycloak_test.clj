;; Added by HudHud Maps, 2026-11-21: tests for Keycloak sign-in (AGPL).
(ns metabase.sso.providers.keycloak-test
  (:require
   [clj-http.client :as http]
   [clojure.test :refer :all]
   [metabase.auth-identity.core :as auth-identity]
   [metabase.auth-identity.provider :as provider]
   [metabase.permissions.core :as perms]
   [metabase.sso.core :as sso]
   [metabase.sso.keycloak.settings :as keycloak.settings]
   [metabase.sso.oidc.discovery :as oidc.discovery]
   [metabase.sso.oidc.state :as oidc.state]
   [metabase.sso.oidc.tokens :as oidc.tokens]
   [metabase.sso.providers.keycloak :as keycloak]
   [metabase.settings.core :as setting]
   [metabase.sso.settings :as sso-settings]
   [metabase.test :as mt]
   [metabase.test.fixtures :as fixtures]
   [toucan2.core :as t2]))

(set! *warn-on-reflection* true)

(use-fixtures :once (fixtures/initialize :db :test-users))

(def ^:private issuer "https://keycloak.example.com/realms/data")

(def ^:private discovery-doc
  {:issuer                 issuer
   :authorization_endpoint (str issuer "/protocol/openid-connect/auth")
   :token_endpoint         (str issuer "/protocol/openid-connect/token")
   :jwks_uri               (str issuer "/protocol/openid-connect/certs")})

(defmacro ^:private with-keycloak-env [& body]
  `(mt/with-temp-env-var-value! [~'mb-keycloak-auth-issuer-uri    issuer
                                 ~'mb-keycloak-auth-client-id     "metabase"
                                 ~'mb-keycloak-auth-client-secret "s3cret"]
     ~@body))

;;; -------------------------------------------------- Pure rules --------------------------------------------------

(deftest ^:parallel provider-hierarchy-test
  (is (auth-identity/isa? :provider/keycloak :provider/oidc))
  (is (auth-identity/isa? :provider/keycloak ::provider/create-user-if-not-exists)))

(deftest email-allowed-test
  (testing "default domain hudhud.sa"
    (is (true? (keycloak/email-allowed? "a.b@hudhud.sa")))
    (is (true? (keycloak/email-allowed? " A.B@HudHud.SA ")))
    (is (false? (keycloak/email-allowed? "a@gmail.com")))
    (is (false? (keycloak/email-allowed? "a@nothudhud.sa")))
    (is (false? (keycloak/email-allowed? "a@hudhud.sa.evil.com")))
    (is (false? (keycloak/email-allowed? "a@evil.com@hudhud.sa")))
    (is (false? (keycloak/email-allowed? nil)))
    (is (false? (keycloak/email-allowed? "")))))

(deftest ^:parallel email-verified-test
  (is (true? (keycloak/email-verified? {:email_verified true})))
  (is (true? (keycloak/email-verified? {:email_verified "true"})))
  (is (false? (keycloak/email-verified? {:email_verified false})))
  (is (false? (keycloak/email-verified? {}))))

(deftest admin-test
  (is (true? (keycloak/admin? {:groups ["data-default-read" "data-admins"]})))
  (is (true? (keycloak/admin? {:groups ["/data-admins"]})))
  (is (false? (keycloak/admin? {:groups ["data-admins-x" "xdata-admins"]})))
  (is (false? (keycloak/admin? {:groups "data-admins"})) "a string, not a list, is refused")
  (is (false? (keycloak/admin? {}))))

(deftest settings-test
  (testing "not configured without all three env values"
    (is (false? (keycloak.settings/keycloak-auth-enabled))))
  (with-keycloak-env
    (is (true? (keycloak.settings/keycloak-auth-enabled)))
    (is (true? (sso-settings/sso-source-enabled? :keycloak))))
  (testing "settings can't be changed through the settings API"
    (is (thrown? Exception (setting/set! :keycloak-auth-client-id "x")))
    (is (thrown? Exception (setting/set! :keycloak-auth-admin-group "everyone")))))

;;; -------------------------------------------------- Login flow --------------------------------------------------

(defn- login! [claims]
  (mt/with-dynamic-fn-redefs [oidc.discovery/discover-oidc-configuration (fn [_] discovery-doc)
                              oidc.state/validate-oidc-callback (fn [& _] {:valid? true :nonce "n" :redirect "/"})
                              http/post (fn [_url _opts] {:status 200 :body {:id_token "t" :access_token "a"}})
                              oidc.tokens/validate-id-token (fn [_ _ _] {:valid? true :claims claims})]
    (auth-identity/login! :provider/keycloak
                          {:code "c" :state "s"
                           :redirect-uri "https://metabase.example.com/auth/sso/keycloak/callback"
                           :device-info {:device_id "d" :device_description "test" :ip_address "127.0.0.1"
                                         :embedded false :token_exchange false}})))

(defn- claims [email & {:as more}]
  (merge {:sub (str "sub-" email) :iss issuer :aud "metabase" :email email :email_verified true
          :given_name "Test" :family_name "Person" :groups ["data-default-read"]}
         more))

(defn- admin-member? [user-id]
  (t2/exists? :model/PermissionsGroupMembership :user_id user-id :group_id (:id (perms/admin-group))))

(deftest new-person-gets-account-not-admin-test
  (with-keycloak-env
    (let [email "kc-new@hudhud.sa"]
      (try
        (let [result (login! (claims email))
              user   (t2/select-one :model/User :email email)]
          (is (true? (:success? result)))
          (is (some? (:session result)) "a session is created")
          (is (= :keycloak (t2/select-one-fn :sso_source :model/User :id (:id user))))
          (is (true? (sso/sso-only-user? (:id user) email :keycloak))
              "Metabase's own rule: no password can be set or reset for this account")
          (is (false? (:is_superuser user)))
          (is (false? (admin-member? (:id user))))
          (is (t2/exists? :model/AuthIdentity :user_id (:id user) :provider "keycloak")))
        (finally (t2/delete! :model/User :email email))))))

(deftest admin-follows-group-test
  (with-keycloak-env
    (let [email "kc-admin@hudhud.sa"]
      (try
        (login! (claims email :groups ["data-admins"]))
        (let [id (t2/select-one-pk :model/User :email email)]
          (is (true? (admin-member? id)) "joins Administrators")
          (is (true? (t2/select-one-fn :is_superuser :model/User :id id)))
          (login! (claims email :groups ["data-default-read"]))
          (is (false? (admin-member? id)) "leaves Administrators at the next sign-in")
          (is (false? (t2/select-one-fn :is_superuser :model/User :id id))))
        (finally (t2/delete! :model/User :email email))))))

(deftest other-groups-untouched-test
  (with-keycloak-env
    (mt/with-temp [:model/PermissionsGroup {group-id :id} {:name "kc-hand-made"}]
      (let [email "kc-other@hudhud.sa"]
        (try
          (login! (claims email))
          (let [id (t2/select-one-pk :model/User :email email)]
            (perms/add-user-to-group! id group-id)
            (login! (claims email))
            (is (t2/exists? :model/PermissionsGroupMembership :user_id id :group_id group-id)))
          (finally (t2/delete! :model/User :email email)))))))

(deftest refusals-test
  (with-keycloak-env
    (testing "other domain"
      (is (= :email-domain-not-allowed (:error (login! (claims "x@gmail.com")))))
      (is (not (t2/exists? :model/User :email "x@gmail.com"))))
    (testing "unverified email"
      (is (= :email-not-verified (:error (login! (claims "kc-unverified@hudhud.sa" :email_verified false)))))
      (is (= :email-not-verified (:error (login! (dissoc (claims "kc-unverified@hudhud.sa") :email_verified)))))
      (is (not (t2/exists? :model/User :email "kc-unverified@hudhud.sa")))))
  (testing "not configured"
    (is (= :keycloak-not-configured (:error (login! (claims "kc-x@hudhud.sa")))))))
