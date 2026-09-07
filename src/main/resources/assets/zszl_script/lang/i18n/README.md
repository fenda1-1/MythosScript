# Modular language resources

All bundled translation entries live in locale-specific feature modules.
The `modules.list` manifest defines load order because a packaged JAR cannot
reliably enumerate resource directories.

The bundled `shadowbaritone` domain uses the same layout at
`assets/shadowbaritone/lang/i18n/`.

`ClientTranslationInjector` loads the bundled `en_us` modules first, then
overlays the selected locale. The mod intentionally ships only Simplified
Chinese and English modules; English remains the fallback module set.

Available locales: `zh_cn`, `en_us`.
