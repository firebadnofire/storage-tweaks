# Licensing and references

The existing repository LICENSE is AGPL v3 with the project's existing
or-later notice. It has not been replaced. New implementation code is covered by
that repository license. Distribution is not automatically authorized as MIT-only.

Behavioral references inspected on 2026-11-22:

* Punch To Deposit: https://github.com/Shafif090/Punch-To-Deposit,
  revision `2aeee6fd5425497993b7365cf88a6463ebfc24d2`. Its actual LICENSE
  is MIT, copyright (c) 2026 Shafi. The source uses ItemTool, ItemSword, ItemBow,
  and ItemShears exclusions, a P settings binding, and chest hover text. Its
  original client-only deposit workaround is deliberately not reused.
* Read-only sibling clientcommands, revision
  `263ca7a6f80af8bf02410469e7621b90e5ac0d22`. Actual LICENSE and metadata
  declare LGPL-3.0-or-later, copyright (c) 2021 Earthcomputer; the supplied brief's
  LGPL-3.0-only description differs. Inspected finder registration, flags,
  privilege checks, progressive container inspection, scheduling, and GuiBlocker.
  The implementation here is independently written, without copying/adapting
  upstream source or assets or introducing a runtime dependency. The user explicitly
  requested unrestricted searches of already received entity/block data and first
  matching container GUI handoff, which differ from this upstream revision.

Minecraft/Forge libraries remain external build/runtime dependencies. The Gradle
wrapper files are standard tooling copied from an existing local project using
Gradle 4.9. Do not commit downloaded Minecraft assets or test worlds. Release review
must confirm accurate dependency notices and corresponding source availability;
no release publication has been performed.
