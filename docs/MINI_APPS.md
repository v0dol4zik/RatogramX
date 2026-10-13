# Mini Apps

Ratogram X provides initial support for bot Mini Apps through TDLib and Android
System WebView. They open in a native Telegram X screen using the current theme.
Android 5.0 or newer and a WebView provider supporting origin-scoped messages
and document-start scripts are required. Unsupported devices show a message
instead of loading a page with an unrestricted JavaScript bridge.

## Entry points

| Entry point | TDLib request |
| --- | --- |
| Inline message button or bot menu button | `OpenWebApp` |
| Reply keyboard button or inline-results button | `GetWebAppUrl` |
| Named Mini App link | `SearchWebApp`, then `GetWebAppLinkUrl` |
| Main Mini App link | `GetMainWebApp` |

Menu buttons containing Telegram Mini App links follow the link route. The
current chat, topic and reply are passed when opening from a bot menu. Links
ask for confirmation, and other entry points ask once per bot and account.
Consent is stored locally and cleared on logout. Opening an app does not
automatically grant the bot permission to send messages to the user.

## Supported events

- Ready, close, expand, theme and viewport requests.
- Main and back button setup, including a disabled main button during progress.
- Native back handling and an optional confirmation before discarding changes.
- External HTTP/HTTPS links in Custom Tabs and Telegram links in the client.
- Standard `bg_color` and `secondary_bg_color` header colors.
- Safe-area requests; the app content is already inset below the native header
  and system bars.
- `web_app_data_send` for reply-keyboard launches, limited to 4096 UTF-8 bytes.

Each `OpenWebApp` launch is closed through `CloseWebApp` when its controller is
destroyed. `UpdateWebAppMessageSent` closes the matching launch, including after
returning from another native screen. Cancelled or obsolete opening requests
also release their launch.

The native bridge accepts messages only from the original HTTPS origin and
the main frame. It never grants access to cross-origin pages or iframes.
`requireSameOrigin` also restricts navigation inside the WebView; when TDLib
allows cross-origin navigation, the other origin still has no native bridge.
Local file/content access, mixed content, third-party cookies, automatic
windows and geolocation are disabled. Launch URLs and app payloads are not
logged by the new controller.

## Follow-up scope and validation

Fullscreen and compact sheet presentation, attachment-menu integration,
arbitrary header RGB colors, haptics, clipboard, CloudStorage, payments and
other privileged API requests are outside this initial implementation. Apps
depending on those APIs may need further support.

Before release, test all entry points with a bot account on a device: consent
acceptance and cancellation, main/back buttons, keyboard data sending,
Telegram/external links, theme changes, rotation, nested screens, closing after
message submission and a WebView provider lacking the required features.
Also verify that a cross-origin redirect or iframe cannot send native events.
JVM tests cover HTTPS origin normalization, invalid addresses, origin/frame
boundaries and the UTF-8 data limit; they do not replace WebView device tests.
