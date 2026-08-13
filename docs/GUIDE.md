# Smart Auto Reconnect - Detailed Guide

A client-side Fabric mod for MC 26.2 that automatically reconnects you
after an **involuntary** disconnect - a kick, a timeout, a dropped
connection. It never fires when you disconnect yourself via the pause
menu.

## Installation

1. Install Fabric Loader for MC 26.2.
2. Install **Fabric API**.
3. Install **Cloth Config API** (required - the config screen depends on it).
4. Optionally install **Mod Menu** for an in-game config entry point;
   without it, edit `config/smartautoreconnect.json` directly.
5. Drop the mod jar from [Releases](../../../releases) into your `mods/` folder.

## What counts as "involuntary"

The mod hooks the exact method the pause-menu **Disconnect** button
calls. If that method didn't run, the disconnect wasn't you clicking the
button - kicks, timeouts, and connection drops all count. Nothing else in
the client calls that method, so there's no ambiguity.

Only direct-connect and server-list multiplayer are in scope. Realms and
LAN worlds are intentionally excluded - there's no meaningful "reconnect"
for either.

## The retry sequence

On an involuntary disconnect:

1. Waits 30 seconds, then attempts to reconnect to the same server.
2. If that fails, waits 50 seconds and tries again.
3. Then 70, then 90, then 110 seconds - 5 attempts total.
4. If all 5 fail, gives up and notifies you (toast, and a sound if
   enabled).

Each wait is measured from when the *previous attempt actually
concluded* (success or failure) - not from when it started. A slow or
hanging connection attempt (e.g. a fully unreachable server that takes a
while to time out) doesn't eat into the next attempt's countdown.

## Rapid-disconnect-loop safeguard

Separately from the 5-attempt sequence above, the mod tracks every
involuntary disconnect (not just failed attempts) in a rolling 5-minute
window. If more than 4 happen in that window, it aborts the entire retry
sequence outright instead of continuing to retry.

This exists for a specific pattern: reconnecting *succeeds* every time,
but you get kicked again almost instantly, over and over. Because a
successful reconnect normally resets the 5-attempt counter back to zero,
that loop would otherwise never trip the "gave up after 5 attempts"
message - it would just retry forever. A pattern like that usually means
something client-side (a ban, a crash loop, a memory problem) rather than
a normal server-side hiccup, so the mod stops and lets you investigate
instead of hammering the server.

Toggle: **Abort on rapid disconnect loop** (default: on).

## Config reference

| Setting | Default | What it does |
|---|---|---|
| Enabled | On | Master switch for the whole mod. |
| Notification mode | Toast and button | See below. |
| Abort on rapid disconnect loop | On | See above. |

**Notification mode:**
- **Toast only** - on-screen toast notifications at every stage
  (disconnected, attempting, attempt failed, reconnected, gave up).
- **Toast and button** (default) - the same toasts, plus extra widgets on
  the vanilla disconnect screen while relevant:
  - A status label with a live countdown (`Retrying in 47s (attempt
    2/5)...`) and a **Cancel Auto-Reconnect** button, shown whenever an
    attempt is scheduled.
  - A **Reconnect Now** button, shown whenever there's a known server to
    reconnect to. Mid-wait, it skips straight to the next attempt. If the
    sequence already gave up (or was cancelled), it fires a single
    one-off attempt instead - that one doesn't schedule further automatic
    retries if it also fails.

## Cancel keybind

A **Cancel reconnect attempts** keybind is registered but ships
**unbound** - bind it yourself under Controls. It stops an in-progress
retry sequence from anywhere (useful if you know the server's about to go
down for maintenance), and does the same thing the disconnect screen's
Cancel button does.

## Integration with Smart Auto Attack / Smart Auto Mine

If either of those mods is installed and was actively running when the
disconnect happened, they detect a successful *scripted* reconnect from
this mod (via a lightweight reflection-based signal - no hard dependency
either way) and resume automatically after a short settle buffer. This
happens regardless of their own "resume after manual reconnect" setting,
which only governs reconnects *you* trigger yourself - including via this
mod's own Reconnect Now button, which counts as manual from their
perspective.

## Troubleshooting

- **It didn't try to reconnect at all.** Check Enabled is on, and that
  you weren't on a Realm or LAN world. Also check the disconnect was
  actually involuntary - pressing Disconnect yourself never triggers it.
- **It gave up almost immediately after only one or two kicks.** That's
  the rapid-disconnect-loop safeguard. If you're sure it's a normal
  server issue (restart loop, maintenance), turn that setting off, or use
  Reconnect Now once things settle.
- **Auto Attack/Mine didn't resume after a scripted reconnect.** Confirm
  those mods are on a build recent enough to include the signal check,
  and that they were actually enabled at the moment of disconnect.

## License

MIT - see [LICENSE](../LICENSE).
