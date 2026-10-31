/**
 * Public API of Alkahest Drops for other mods. Everything in this package and its subpackages follows semantic
 * versioning: no breaking change within a major version, and anything removed is deprecated for at least one minor
 * version first. Each type and method says in {@code @since} the version that added it. Classes outside this package
 * are internal and may change in any release; do not depend on them.
 *
 * <p>Entry points: {@link com.jaffh.alkahestdrops.api.AlkahestDropsApi} to read what drop files do to a mob or block
 * and to read or reset player progress, {@link com.jaffh.alkahestdrops.api.event.OnDropEvent} to react when a drop
 * spawns, and {@link com.jaffh.alkahestdrops.api.event.DropFilesLoadedEvent} to know when the files changed.
 *
 * @since 1.0.0
 */
@ParametersAreNonnullByDefault
package com.jaffh.alkahestdrops.api;

import javax.annotation.ParametersAreNonnullByDefault;
