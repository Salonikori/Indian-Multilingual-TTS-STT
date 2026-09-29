# Phase 4: TTS and Alert Path Testing Guide

## Overview
This phase tests TTS streaming behavior and alert path functionality across different phone states.

## Setup Instructions

1. **Load a Language Model**
   - Open iTantra app
   - Select Hindi or English language
   - Press "Load active language" button
   - Wait for "Loaded [Language]" confirmation

2. **Access TTS Test Screen**
   - Press "TTS test · synthesize + alert" button
   - This opens the enhanced TTS test interface

## Test 1: Streaming TTS Verification

### Goal
Confirm that the first sentence starts playing before the complete text is synthesized.

### Steps
1. Select sample text #3 (longest text) in either language
2. Keep in "Normal" mode (not Alert)
3. Press "▶ Play Normal"
4. **Watch the console logs** (via `adb logcat` or Android Studio)
5. **Listen carefully** - you should hear the first sentence start while synthesis continues

### Expected Behavior
- Console shows: "Synthesizing sentence 1/3", "Playing sentence 1/3", then "Synthesizing sentence 2/3"
- Audio playback begins before all sentences are synthesized
- "Time to first audio" metric shows reasonable latency (< 2000ms)

## Test 2: Alert Path Testing

### Test 2a: Phone in Silent Mode
1. Put phone in silent/vibrate mode
2. Select any sample text
3. Switch to "Alert" mode
4. Press "▶ Play Alert"
5. **Expected**: Alert plays at full volume despite silent mode

### Test 2b: Screen Off
1. Turn off phone screen
2. Select sample text, switch to "Alert" mode
3. Press "▶ Play Alert" quickly before screen fully locks
4. **Expected**: Alert plays audibly with screen off

### Test 2c: Another App Playing Music
1. Start music app (Spotify, YouTube Music, etc.)
2. Return to iTantra TTS test
3. Play alert message
4. **Expected**: 
   - Music ducks/pauses during alert
   - Alert is clearly audible
   - Music resumes after alert

### Test 2d: Do Not Disturb Enabled
1. Enable Do Not Disturb mode
2. Test alert playback
3. **Expected**: Results may vary by Android version/OEM
4. **Document**: What actually happens on your device

## Test 3: Volume Restoration

### Steps
1. Note current alarm volume level
2. Play an alert message (any sample text in Alert mode)
3. Check alarm volume after alert completes
4. **Expected**: Alarm volume returns to original level

### Verification
- Go to Settings > Sounds > Volume
- Check that alarm volume slider is at original position

## Test 4: Audio Quality Verification

### Normal Mode Testing
- Test all 3 sample texts in both languages
- Verify speech is clear and intelligible
- No clipping, distortion, or cutoffs

### Alert Mode Testing  
- Attention tone plays before speech
- Speech remains intelligible after tone
- No audio artifacts from volume changes

## Success Criteria

✅ **Streaming Confirmed**: First sentence audible before full synthesis  
✅ **Silent Mode Bypass**: Alert audible when phone is silent  
✅ **Screen-Off Playback**: Alert works with screen off  
✅ **Music Interruption**: Alert preempts other audio  
✅ **DND Behavior**: Document actual behavior  
✅ **Volume Restoration**: Original alarm volume restored  
✅ **Audio Quality**: Clear, intelligible speech in all conditions  

## Troubleshooting

**No TTS Test Button Available**
- Ensure language is loaded first
- Check that TTS models are installed

**No Audio During Test**
- Check device volume settings
- Verify audio output device (speakers/headphones)
- Try different sample texts

**Console Logs Not Visible**
- Use: `adb logcat | grep "TtsPipeline\|TtsTestActivity"`
- Or monitor in Android Studio logcat

**Alert Not Overriding Silent Mode**
- Some OEMs may restrict alarm stream behavior
- Document actual behavior on your device

## Technical Details

### Streaming Implementation
- Text split into sentences using `SherpaTtsEngine.splitSentences()`
- Producer-consumer pattern with `Channel<TtsChunk>`
- Each sentence synthesized and played independently
- Concurrent synthesis and playback for reduced latency

### Alert Path Implementation
- Uses `USAGE_ALARM` audio stream
- Requests `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`
- Maximizes alarm volume during playback
- Restores original volume after completion
- Includes attention tone before speech

## Phase 4 Completion

Phase 4 is **COMPLETE** when:
- [ ] TTS Test screen accessible and functional
- [ ] Streaming behavior verified (first sentence starts before full synthesis)
- [ ] Alert path tested in all 4 conditions
- [ ] Volume restoration confirmed
- [ ] Audio quality verified as clear and intelligible

Document any device-specific behaviors or limitations observed during testing.