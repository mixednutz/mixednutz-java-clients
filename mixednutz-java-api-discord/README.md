# mixednutz-java-api-discord

Discord adapter for MixedNutz. It lets a MixedNutz user cross-post to text channels on a
Discord server through a **bot** that the site owner has added to that server.

This module is part of the [mixednutz-java-clients](../) reactor. Its version is the
reactor's version.

## Feature status

| Feature | Status |
|---|---|
| Post a message to a server text channel (choose the channel per post, or use the default channel) | ✅ Works |
| List the text channels the bot can write to (shown in the compose form) | ✅ Works |
| Read a Discord timeline into MixedNutz (`getTimelineClient()`) | ❌ Not implemented, returns `null` (the feed poller logs "returned null page") |
| User lookup (`getUserClient()`), groups (`getGroupClient()`) | ❌ Not implemented |
| Roles / external lists (`getRoleClient()`) | ❌ Reports no roles |

There is no per-user Discord login. Every connected MixedNutz account posts as the same bot,
using the site-wide bot token.

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
        <artifactId>mixednutz-java-api-discord</artifactId>
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

The adapter brings in **Javacord 3.7.0** as a normal compile dependency.

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

* **`DiscordConfig`** (package `net.mixednutz.api`) is annotated `@Profile("discord")`. Add
  `discord` to `spring.profiles.active`.
* **The host must component-scan `net.mixednutz.api`.** app-server does this in `SocialConfig`.
  `DiscordConfig` then scans `net.mixednutz.discord`, which picks up `DiscordConnectController`.

### 4. Properties

| Property | app-server variable | Required | Meaning |
|---|---|---|---|
| `mixednutz.social.discord.token` | `DISCORD_TOKEN` | yes | Bot token from the Discord Developer Portal (Bot → Token) |
| `mixednutz.social.discord.default-channel-id` | `DISCORD_CHANNEL_ID` | yes | Numeric ID of the text channel used when a post doesn't choose one |

app-server's `application.yml`:

```yaml
---
spring:
  config.activate.on-profile: discord

mixednutz:
  social:
    discord:
      token: ${DISCORD_TOKEN}
      default-channel-id: ${DISCORD_CHANNEL_ID}
```

Pass the values as environment variables or as `--DISCORD_TOKEN=...` program arguments.
Never commit the token.

### 5. Beans the host must provide

`DiscordConfig` and the connect controller expect these beans, which app-server defines in
`SocialConfig` and `NewExternalCredentialsController`:

* **`CredentialsCallback`:** saves the external credentials and creates the external feed
  after a connect. app-server's is `NewExternalCredentialsCallback`.
* **`ConnectionRepository`:** request-scoped and tied to the signed-in user.
* **`ConnectionFactoryLocator`** and **`ApiProviderRegistry`:** these collect every
  `ConnectionFactory` and `ApiProvider` bean. That's how `DiscordConnectionFactory` and
  `DiscordProvider` (provider ID `discord`) get registered.

### 6. UI

* **Compose form:** app-server's `DiscordComponentSettings` (`manager/post/discord`, active via
  `@ConditionalOnClass(name="net.mixednutz.api.DiscordConfig")`) adds the compose modal
  fragment `discord/fragments_discord :: discordCompose_form`. That template is in this jar.
  The form posts to `/feed/post` with `externalFeedId`, `composeBody`, `channelIdAsString` and
  `media`.
* **Connecting:** a signed-in user connects with `POST /connect/discord`. This creates the
  connection with the site bot, and no Discord login happens.

## Discord-side setup

1. Create an application in the Discord Developer Portal, add a **Bot** and copy its token.
2. Invite the bot to your server with permission to **View Channels** and **Send Messages** in
   the channels you want to post to. Only text channels where the bot can write are offered.
3. To get the default channel ID, enable Developer Mode in Discord, then right-click the
   channel and choose **Copy Channel ID**.

## Notes

* **Each API call logs the bot in.** `DiscordServiceProvider.getApi()` performs a full Javacord
  login (`login().join()`).
* **Failed sends return `null`.** Errors are logged as "Error sending message to Discord" and
  `postToTimeline` returns `null` instead of throwing an exception.
