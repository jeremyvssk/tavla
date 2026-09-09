# Skills

Two groups.

## Vendored (frontend design engineering) — local only, **not committed**

`animate`, `emil-design-eng`, `pick-ui-library`, `prototype` are copied unmodified from
[emilkowalski/skills](https://github.com/emilkowalski/skills), MIT licensed — see
`VENDOR-LICENSE.md`. They carry Emil Kowalski's design and animation philosophy, and mostly
earn their keep in Project 3 (the full UI).

**These four are gitignored.** They are generic tooling with nothing project-specific in them,
and they were 85% of the skills payload by line count — a reviewer browsing this repo would
have found mostly writing nobody on the project did. They still work locally; they just don't
ship. To reinstate on another machine, re-copy the directories from upstream. Nothing here is
patched locally except the frontmatter line below.

**All four are `disable-model-invocation: true`**, so none of them can fire on their own —
they run only when you type `/animate`, `/emil-design-eng`, `/pick-ui-library` or `/prototype`.
Upstream ships that flag on `pick-ui-library` and `prototype`; it was added here to
`emil-design-eng` and `animate` so that no design skill can trigger itself during backend work.
Their description lines still sit in context permanently (~290 tokens for the four); only the
bodies — about 17k tokens — are deferred until invoked.

Deliberately **not** vendored: `animate-expo` and `write-swift` (no React Native, no Swift here),
`ask-sonner` (not using Sonner), `apple-design`, `animation-vocabulary`, `review-animations`,
`improve-animations`, `find-animation-opportunities` (P3 polish at best, and the last three
overlap the built-in `/code-review`).

To update: re-copy the directory from upstream. Nothing here is patched locally.

## Project skills

`new-endpoint`, `verify` and `flyway-migration` encode this repo's own rules. They exist partly
to *shrink* context: the checklists they hold used to sit in `backend/CLAUDE.md`, which loads on
every single turn, and now load only when the work actually calls for them.

Routing — which skill applies to what — is in the root [CLAUDE.md](../../CLAUDE.md#skills).
