# Asset Attribution

This file records the static visual assets distributed with the source
repository. Audit date: 2026-08-30.

## Project launcher icon

The purple, circuit-style launcher icon is an original visual asset created by
the Social First Aid Kit project team. The following Android resource files are
different density or packaging variants of that same team-created artwork:

- `app/src/main/ic_launcher-playstore.png`
- `app/src/main/res/drawable/ic_launcher_full.webp`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/mipmap-*/ic_launcher.webp`
- `app/src/main/res/mipmap-*/ic_launcher_round.webp`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

The project team permits these icon files to be redistributed with this
repository under the Apache License 2.0. The artwork is not an Android or
Google asset and does not imply endorsement by either organization.

## Material Icons

The UI uses icons supplied by the `androidx.compose.material:material-icons-extended`
dependency. Those icons remain subject to their upstream Apache License 2.0
terms; see `THIRD_PARTY_NOTICES.md` for dependency details.

## User-provided runtime media

User avatars, chat screenshots, moment images, and model-generated images are
provided or created at runtime. They are not static assets distributed by this
repository. Users are responsible for ensuring that they have the rights to
use any runtime media they provide.

## Maintenance

When adding an image, font, audio, video, or other visual asset, record its
source, author, license, modifications, and repository path before merging.
