Bugs and Features:

**Bugs:**

- [ ] App crashes when location returns null (Line 360)
- [ ] If watch sleeps when STT screen is active you have to swipe back.  Can we keep the watch awake during STT.
- [ ] Image needs to be set to null on button press for all states
- [ ] Background instability



**Features:**

- **User Interface:**
  - [ ] Resize image buttons to 100% screen width/height
  - [ ] Update ambient mode appearance (e.g., simple clock analog hands with color denoting mimicing connected state)
  - [X] Square image orientation
  - [ ] Adjust max tokens / temp in settings
  - [ ] Add toggle for image display in settings
  - [ ] Real notifications when app is in background and message is received
  - [ ] Performance and battery optimization issues
  - [ ] Pinch zoom image

- **Bluetooth & Audio:**
  - [ ] Bluetooth headphones awareness: Request important information on connect
  - [ ] Trigger Speech-to-Text (STT) on Bluetooth connect and foreground app trigger
  - [ ] Toggle Text-to-Speech (TTS) in settings
  - [ ] Haptics on async response, delayed TTS play until user click
  - [ ] Repeat last TTS
  - [ ] Delay between TTS end and STT restart
  - [ ] Whisper instead of local STT (Toggle)
  - [ ] XTTS instead of Elevenlabs TTS (Toggle)

- **Notifications & Responses:**
  - [X] Use "loading1.gif" as ACK for initial message received and processing underway
  - [ ] Vibrate/wake on response feature and setting toggles

- **Miscellaneous:**
  - [ ] Echomind branding
  
