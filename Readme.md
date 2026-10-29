<div align="center">
<a href="https://github.com/tdbe/Play-On-Connect">
  <img width="900" alt="tv_banner" src="https://github.com/user-attachments/assets/691b2d0a-e749-42cb-86f2-24dfdf8d0282" />
</a>
</div>

<div align="center">
<a href="https://github.com/tdbe/Play-On-Connect">
  <img height="860" alt="Screenshot_202610254_Play On Connect" src="https://github.com/user-attachments/assets/6af972b8-0dc5-4879-a967-7fd24d087cbc" />
</a>
<a href="https://github.com/tdbe/Play-On-Connect">
  <img height="720" alt="Screenshot_202610137_Play On Connect" src="https://github.com/user-attachments/assets/816571d9-0037-4396-a966-e1389e7e9d68" />
</a>
<a href="https://github.com/tdbe/Play-On-Connect">
  <img height="720" alt="Screenshot_202610038_Play On Connect" src="https://github.com/user-attachments/assets/04a2b65f-f5be-475b-984b-f3668a3998f9" />
</a>
</div>

# Play On Connect

An Android 9+ automation tasker style app that creates rules to play music file(s) or urls (e.g. youtube, radio) on events. Can be layered, sleeped + faded, looped, auto-stopepd etc. Responds to e.g. bluetooth device (dis)connect or (recurring) time alarms. Works in background.

New triggers and/or actions may be added later. For now the main focus is playing playlists, soundscapes, white noise, and conveniently triggering them e.g. when you turn on a specific bluetooth speaker.

To this effect, there's also a setting to `"Play in parallel to system media"` so you can e.g. listen to soothing white noise while doomscrolling tiktok.


## Rules:

1 Rule = 1 Trigger + 1 Action.

Rules can be layered: 
- All Enabled rules with the same Bluetooth Device Connected Trigger will be triggered at the same time and play in parallel with their independent settings. 
- All Time rules will play in parallel (you can e.g. layer rules with different start times). 

Otherwise when a new rule starts it will stop the previously started rules e.g.: 
- a bluetooth device triggered rule stops a previously started time triggered rule(s), 
- a time triggered rule stops a preivously started bluetooth device triggered rule(s), 
- a `MyBluetoothDeviceA` triggered rule stops a previlusly started `MyOtherBluetoothDeviceB` triggered rule.

### Triggers:

- On Bluetooth device connected/disconnected (choose device from a list of already paired devices).
- On Time: `hh:mm:ss repeat:Mo,Tu,We,Th,Fr,Sa,Su`


### Rules:

- Play music file(s) using an internal player (browse for one or more audio files).
	- Settings:
		- `Loop Playback`: if on, will loop the sound file (or the files playlyst) forever or until the `Stop After` time is hit.
		- `Stop After`: stop the playback after minutes (field also supports e.g `0.5` minutes).
		- `Fade Out Over`: how many seconds before the calculated end of playback we should linearly fade out the volume of this internal player. (does not affect OS media volume) (is clamped to at most the `Stop After` time amount
- Open a URL in either a specific browser or a specific dedicated app
	- Settings: 
		- `Stop After`: send a OS media pause event after minutes (field also supports e.g `0.5` minutes).
		- Sends the OS media pause event when a new rule is triggered that isn't layerable to this one.	
		
## Performance, Permissions, and Warranty

The app listens passively for events, so there's no battery waste. The most active thing it does besides play media, is during a fade-out period it lowers the internal player volume every second until finished. The fade-out period is scheduled and known at play-start time.

The app doesn't browse the network or internet.

The Android `Overlay` (`SYSTEM_ALERT_WINDOW`) is optional; use it if some app features don't work in the background autonomously - the opening of URLs is a Foreground Action and requires this permission on most devices (unless the app is somehow active as far as the OS foreground is concerned).

You can see all permissions in `PlayOnConnect/app/src/main/AndroidManifest.xml`.

This repo is a basic kotlin + Compose gradle project, and it's tweaked, tested, iterated by [me](https://github.com/tdbe) but slopped using Qwen 3.8 Flash Next 180B a6B Q4. It's just an unimportant toy so I haven't read through the inconsequential parts of the code. I architected it thoroughly but in words (vibes as it were). It's provided openly for free as-is.

## Install

Get the latest Release .apk file from the releases section and put it on the device and tap it to install. You can also use the Obtainium / ObtainX "store" to track it and install from there.

## Build It Yourself

Open this folder in Android Studio, have some target sdk installed e.g. 36 or 37 (which includes 28 (android 9)) (via the gear icon > SDK Manager), and from top bar menu click Build > Generate App Bundles or APKs > Generate APKs.
