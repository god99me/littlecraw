---
name: asr-transcription
description: Local audio speech-to-text transcription for voice messages and audio files using a bundled Python script powered by faster-whisper. Use when the user sends audio, asks to transcribe speech, wants a voice note summarized, or needs ASR on local media files.
---

# ASR Transcription

Use the bundled script to transcribe local audio files.

## Workflow

1. Prefer local files already available on disk.
2. Run `scripts/transcribe.py <audio-path>`.
3. Use `--model small` for better accuracy or `--model tiny` for faster startup.
4. Use `--language zh` or another language code when the spoken language is known.
5. Return the plain transcript first; summarize only if the user asks.

## Commands

Basic:

```bash
/home/admin/.openclaw/workspace/.venv-asr/bin/python /home/admin/.openclaw/workspace/skills/asr-transcription/scripts/transcribe.py <audio-path>
```

Faster startup:

```bash
/home/admin/.openclaw/workspace/.venv-asr/bin/python /home/admin/.openclaw/workspace/skills/asr-transcription/scripts/transcribe.py <audio-path> --model tiny
```

Known language:

```bash
/home/admin/.openclaw/workspace/.venv-asr/bin/python /home/admin/.openclaw/workspace/skills/asr-transcription/scripts/transcribe.py <audio-path> --language zh --model small
```

## Notes

- The first run downloads the selected Whisper model, so it may take longer.
- The script prints JSON with `text`, `language`, `duration`, and `segments`.
- Keep responses concise when the user only wants the transcript.
