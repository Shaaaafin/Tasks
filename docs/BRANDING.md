# Launcher artwork

The launcher symbol reproduces the dot and three staggered rounded bars in the supplied reference sheet. The sheet title is not used as the application name; the provisional application label remains Tasks.

The artwork is maintained as Android vectors rather than a screenshot or a bitmap containing captions. Adaptive icons allow Android launchers to apply their chosen mask. The API 33 monochrome layer supports launcher-themed icons.

| Resource | Purpose |
| --- | --- |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Sage dot/top bar and theme-aware lower bars |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Single-color symbol for themed icons |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive background and foreground |
| `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml` | Adaptive icon with monochrome support |
| `app/src/main/res/values/launcher_colors.xml` | Cream background and espresso lower bars |
| `app/src/main/res/values-night/launcher_colors.xml` | Espresso background and white lower bars |

The accent is `#4CA987`, cream is `#F4EDE2`, and espresso is `#28211E`. Native development renders are available in `previews/logo-light.png` and `previews/logo-dark.png`.

Launcher resources follow the system/launcher theme and may be cached by the launcher. A forced light/dark theme chosen within the app affects Compose and the widget; it does not force a separate launcher to change its configuration.
