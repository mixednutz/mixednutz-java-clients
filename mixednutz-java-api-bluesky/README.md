# mixednutz-java-api-bluesky

Bluesky (AT Protocol) adapter for MixedNutz. It lets a MixedNutz user cross-post to a
**single Bluesky account** configured for the whole site. That can include a link card with
title, description and thumbnail.

This module is part of the [mixednutz-java-clients](../) reactor. Its version is the
reactor's version.

## Feature status

| Feature | Status |
|---|---|
| Create a post on the configured account | ✅ Works |
| Link card (external embed) with title, description and thumbnail | ✅ Works, but needs the embed metadata service (see Properties) |
| Session handling (`createSession` / `refreshSession`, JWT expiry tracking) | ✅ Works |
| Read a Bluesky timeline into MixedNutz (`getTimelineClient()`) | ❌ Not implemented, returns `null` (the feed poller logs "returned null page") |
| User lookup (`getUserClient()`), groups (`getGroupClient()`) | ❌ Not implemented |
| Roles / external lists (`getRoleClient()`) | ❌ Reports no roles |

There is no per-user Bluesky login. Every connected MixedNutz account posts as the account set
in the properties.

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
        <artifactId>mixednutz-java-api-bluesky</artifactId>
        <version>${mixednutz-java-clients.version}</version>
    </dependency>
    <!-- REQUIRED: the adapter declares core as provided, so it is NOT pulled in transitively -->
    <dependency>
        <groupId>net.mixednutz</groupId>
        <artifactId>mixednutz-java-api-core</artifactId>
        <version>${mixednutz-java-clients.version}</version>
    </dependency>
    <!-- also provided-scope in the adapter: spring-social-web 1.1.6.RELEASE,
         spring-boot-starter-web, spring-web, jackson-datatype-jsr310 -->
</dependencies>
```

The adapter brings in **com.auth0:java-jwt 4.4.0** as a normal compile dependency. It's used to
read session token expiry.

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

* **`BlueskyConfig`** (package `net.mixednutz.api`) is annotated `@Profile("bluesky")`. Add
  `bluesky` to `spring.profiles.active`.
* **The host must component-scan `net.mixednutz.api`.** app-server does this in `SocialConfig`.
  `BlueskyConfig` then scans `net.mixednutz.bluesky`, which picks up `BlueskyConnectController`.
* **`BlueskyConfig` needs a `RestTemplateBuilder` bean.** Spring Boot's web auto-configuration
  provides it.

### 4. Properties

| Property | app-server variable | Required | Meaning |
|---|---|---|---|
| `mixednutz.social.bluesky.handle` | `BLUESKY_HANDLE` | yes | Account handle, e.g. `yourname.bsky.social` |
| `mixednutz.social.bluesky.password` | `BLUESKY_PASSWORD` | yes | Use an **App Password** (Bluesky Settings → Privacy and security → App passwords), not the account password |
| `mixednutz.social.bluesky.pds-host` | `BLUESKY_PDS_HOST` | yes | PDS base URL with scheme and no trailing slash, e.g. `https://bsky.social` |
| `mixednutz.social.bluesky.embed-metadata-service-url` | (hard-coded in `application.yml`) | for link cards | URL prefix of a metadata service. The page URL is appended to it, so it should end with something like `?url=` |

app-server's `application.yml`:

```yaml
---
spring:
  config.activate.on-profile: bluesky

mixednutz:
  social:
    bluesky:
      handle: ${BLUESKY_HANDLE}
      password: ${BLUESKY_PASSWORD}
      pdsHost: ${BLUESKY_PDS_HOST}
      embedMetadataServiceUrl: https://embed.example.com/metadata?url=   # your metadata service
```

Pass the secrets as environment variables or as `--BLUESKY_PASSWORD=...` program arguments.
Never commit them.

**About the embed metadata service:** `EmbedClient` sends `GET {embedMetadataServiceUrl}{url}`
and expects JSON with at least `title`, `description` and `imageUrl`. If `imageUrl` is set, the
image is downloaded and uploaded to Bluesky as the card thumbnail. The property has no
default, so if it isn't set, creating a link card fails.

### 5. Beans the host must provide

`BlueskyConfig` and the connect controller expect these beans, which app-server defines in
`SocialConfig` and `NewExternalCredentialsController`:

* **`CredentialsCallback`:** saves the external credentials and creates the external feed
  after a connect. app-server's is `NewExternalCredentialsCallback`.
* **`ConnectionRepository`:** request-scoped and tied to the signed-in user.
* **`ConnectionFactoryLocator`** and **`ApiProviderRegistry`:** these collect every
  `ConnectionFactory` and `ApiProvider` bean. That's how `BlueskyConnectionFactory` and
  `BlueskyProvider` (provider ID `bluesky`) get registered.

### 6. UI

* **Compose form:** app-server's `BlueskyComponentSettings` (`manager/post/bluesky`, active via
  `@ConditionalOnClass(name="net.mixednutz.api.BlueskyConfig")`) adds the compose modal
  fragment `bluesky/fragments_bluesky :: blueskyCompose_form`. That template is in this jar.
  The form posts to `/feed/post` with `externalFeedId`, `composeBody` and `media`.
* **Connecting:** a signed-in user connects with `POST /connect/bluesky`. This creates the
  connection with the configured account, and no Bluesky login page is involved.

## Notes

* **The PDS host is used as given.** Requests go to `{pdsHost}/xrpc/com.atproto.server.createSession`
  and `{pdsHost}/xrpc/com.atproto.server.refreshSession`, so the scheme must be included.
* **Session lifetimes are logged.** Session creation and refresh log when the access and
  refresh JWTs expire.
