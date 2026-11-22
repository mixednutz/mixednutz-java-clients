# mixednutz-java-api-patreon

Patreon adapter for MixedNutz. Today it's used **only to provide credentials**: a MixedNutz
user connects their Patreon account through OAuth2, and MixedNutz stores the resulting
credentials. Those credentials also let MixedNutz read Patreon **tiers**. app-server uses the
tiers as "external lists", so a post's visibility can be limited to the patrons of a tier.
**You can't post to Patreon** with this module.

This module is part of the [mixednutz-java-clients](../) reactor. Its version is the
reactor's version.

## Feature status

| Feature | Status |
|---|---|
| Connect a Patreon account (OAuth2) and store its credentials | ✅ Works |
| Read the user's identity and campaigns (Patreon API v2) | ✅ Implemented (`PatreonTemplate`) |
| Tiers as external lists, for post visibility (`TierAdapter`, through `getRoleClient()`) | ✅ Works. `getAvailableRoles()` returns the tiers of the creator's campaigns, and `getRolesAssigned()` returns the tiers the connected user is a patron of. app-server reads them through `ExternalFeedManager.getExternalLists` / `getExternalListsUserIsOn`. |
| Post to Patreon | ❌ Not supported. `canPost()` returns `false`, `create()` and `postToTimeline()` return `null`. |
| Read a Patreon timeline into MixedNutz (`getTimelineClient()`) | ❌ Not implemented, returns `null` |
| User lookup (`getUserClient()`), groups (`getGroupClient()`) | ❌ Not implemented |

Unlike Discord and Bluesky, Patreon has **no compose form**, because app-server has no
`PatreonComponentSettings`.

## Adding it to the main project (mixednutz-app-server)

### 1. Dependencies

All of this module's framework dependencies have `provided` scope, so the host project has to
supply them. app-server already does.

```xml
<properties>
    <mixednutz-java-clients.version>2.2.0.1</mixednutz-java-clients.version>
</properties>

<dependencies>
    <!-- the adapter -->
    <dependency>
        <groupId>net.mixednutz</groupId>
        <artifactId>mixednutz-java-api-patreon</artifactId>
        <version>${mixednutz-java-clients.version}</version>
    </dependency>
    <!-- REQUIRED: the adapter declares core as provided, so it is NOT pulled in transitively -->
    <dependency>
        <groupId>net.mixednutz</groupId>
        <artifactId>mixednutz-java-api-core</artifactId>
        <version>${mixednutz-java-clients.version}</version>
    </dependency>
    <!-- also provided-scope in the adapter: spring-social-web 1.1.6.RELEASE,
         spring-boot-starter-web, spring-boot-starter-security, spring-web,
         jackson-datatype-jsr310 -->
</dependencies>
```

### 2. Maven repository

Artifacts are published to GitHub Packages. Reading them needs a GitHub token with
`read:packages`, set in `~/.m2/settings.xml` under the matching `<server>` id.

```xml
<repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/mixednutz/mixednutz-java-clients</url>
</repository>
```

### 3. Spring profile and component scanning

* **`PatreonConfig`** (package `net.mixednutz.api`) is annotated `@Profile("patreon")`. Add
  `patreon` to `spring.profiles.active`.
* **The host must component-scan `net.mixednutz.api`.** app-server does this in `SocialConfig`.

### 4. Properties

| Property | app-server variable | Required | Meaning |
|---|---|---|---|
| `mixednutz.social.patreon.client-id` | `PATREON_CLIENT_ID` | yes | OAuth client ID from the Patreon developer portal |
| `mixednutz.social.patreon.client-secret` | `PATREON_CLIENT_SECRET` | yes | OAuth client secret |
| `applicationUrl` | (program argument, e.g. `--applicationUrl=https://www.example.com`) | yes, in practice | Public base URL of the site. Spring Social builds the OAuth redirect URI from it. |

app-server's `application.yml`:

```yaml
---
spring:
  config.activate.on-profile: patreon

mixednutz:
  social:
    patreon:
      client-id: ${PATREON_CLIENT_ID}
      client-secret: ${PATREON_CLIENT_SECRET}
```

Pass the values as environment variables or as `--PATREON_CLIENT_SECRET=...` program
arguments. Never commit them.

### 5. Beans the host must provide

`PatreonConfig` expects these beans, which app-server defines in `SocialConfig` and
`NewExternalCredentialsController`:

* **`CredentialsCallback`:** saves the external credentials and creates the external feed
  after a connect. app-server's is `NewExternalCredentialsCallback`.
* **`ConnectionRepository`:** request-scoped and tied to the signed-in user.
* **`ConnectionFactoryLocator`** and **`ApiProviderRegistry`:** these collect every
  `ConnectionFactory` and `ApiProvider` bean. That's how `PatreonConnectionFactory` and
  `PatreonProvider` (provider ID `patreon`) get registered.
* **The OAuth `ConnectController`:** app-server's `NewExternalCredentialsController.ConnectController`
  extends Spring Social's `ConnectController`, which handles `/connect/patreon`. This module's
  own `PatreonConnectController` is intentionally disabled (`@Controller` is commented out).

## Patreon-side setup and the connect flow

1. Register an OAuth client in the Patreon developer portal and copy the client ID and secret.
2. Add this redirect URI to the client: **`{applicationUrl}/connect/patreon`**. For example:
   * a deployed site: `https://www.example.com/connect/patreon`
   * local development: `https://127.0.0.1:8443/connect/patreon`
3. A signed-in user goes through Spring Social's standard `ConnectController` flow:
   * **`GET /connect/patreon`** renders the view `connect/patreonConnect`, which is shipped in
     this jar.
   * **`POST /connect/patreon`** redirects to `https://www.patreon.com/oauth2/authorize`.
   * **The callback, `GET /connect/patreon?code=...`,** exchanges the code at
     `https://www.patreon.com/api/oauth2/token`. `CredentialsCallback` then stores the
     credentials.

**OAuth scopes:** the module doesn't set any scopes itself. Spring Social's `ConnectController`
sends a `scope` parameter only if the connect form submits one. Reading campaigns and tiers
needs the corresponding Patreon scopes.

## Notes

* **Token refreshes go to Patreon.** Refreshing an expired access token calls
  `https://www.patreon.com/api/oauth2/token`, so it needs outbound access to patreon.com.
* **The connect page depends on app-server templates.** `templates/connect/patreonConnect.html`
  pulls in `header :: navbar`, `footer :: footer`, `feeds/fragments_feed :: newAccountForm_model`
  and `google_analytics :: tracking`, so a host without those templates must provide its own
  `connect/patreonConnect` view.
