// Added by HudHud Maps, 2026-11-21: Keycloak sign-in for our own build of Metabase (free edition).
// Licensed under the GNU Affero General Public License, as the rest of Metabase outside `enterprise/`.
import { KeycloakButton } from "metabase/auth/components/KeycloakButton";
import {
  PLUGIN_AUTH_PROVIDERS,
  PLUGIN_IS_PASSWORD_USER,
} from "metabase/plugins";
import MetabaseSettings from "metabase/utils/settings";

PLUGIN_AUTH_PROVIDERS.providers.push((providers) => {
  const keycloakProvider = {
    name: "keycloak",
    Button: KeycloakButton,
  };

  return MetabaseSettings.get("keycloak-auth-enabled")
    ? [keycloakProvider, ...providers]
    : providers;
});

PLUGIN_IS_PASSWORD_USER.push((user) => user.sso_source !== "keycloak");
