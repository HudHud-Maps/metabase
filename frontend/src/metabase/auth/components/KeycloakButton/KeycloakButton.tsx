// Added by HudHud Maps, 2026-11-21: Keycloak sign-in for our own build of Metabase (free edition).
// Licensed under the GNU Affero General Public License, as the rest of Metabase outside `enterprise/`.
import { t } from "ttag";

import { useSetting } from "metabase/settings";
import { Anchor, Stack, Text } from "metabase/ui";

import { AuthCardButton, AuthTextButton } from "../AuthButton";

interface KeycloakButtonProps {
  redirectUrl?: string;
  isCard?: boolean;
}

const keycloakUrl = (redirectUrl?: string) =>
  redirectUrl
    ? `/auth/sso/keycloak?redirect=${encodeURIComponent(redirectUrl)}`
    : "/auth/sso/keycloak";

export const KeycloakButton = ({ redirectUrl, isCard }: KeycloakButtonProps) => {
  const sourceUrl = useSetting("hudhud-build-source-url");
  const signIn = () => window.location.assign(keycloakUrl(redirectUrl));
  const label = t`Sign in with HudHud (Keycloak)`;

  return (
    <Stack gap="xs" align="center">
      {isCard ? (
        <AuthCardButton onClick={signIn}>{label}</AuthCardButton>
      ) : (
        <AuthTextButton onClick={signIn}>{label}</AuthTextButton>
      )}
      {isCard && sourceUrl && (
        // AGPL section 13: the source of this build
        <Text size="sm" c="text-secondary">
          <Anchor href={sourceUrl} target="_blank" rel="noreferrer">
            {t`Source code of this Metabase build`}
          </Anchor>
        </Text>
      )}
    </Stack>
  );
};
