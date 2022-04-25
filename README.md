# ktor-build-plugins

Ktor Plugins for Build Systems

## Template source

The template for the plugin is taken
from [gh:cortinico/kotlin-gradle-plugin-template](https://github.com/cortinico/kotlin-gradle-plugin-template).

## Steps to take

1. Find `main()` method (mockable) — **NOT READY** (is it needed?)
2. Add shadow plugin (mockable) — **READY**
3. Configure fat jar (mockable) — **READY** (not needed)
4. Add the brand-new task to generate fat jar (non-mockable) — **READY**
5. Publish the plugin — **NOT READY** (is it needed?)