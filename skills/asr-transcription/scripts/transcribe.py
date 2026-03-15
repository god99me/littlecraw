#!/home/admin/.openclaw/workspace/.venv-asr/bin/python
import argparse
import json
from pathlib import Path

from faster_whisper import WhisperModel


def main() -> int:
    parser = argparse.ArgumentParser(description="Transcribe local audio with faster-whisper")
    parser.add_argument("audio_path")
    parser.add_argument("--model", default="tiny", help="Whisper model name")
    parser.add_argument("--device", default="cpu", help="Inference device")
    parser.add_argument("--compute-type", default="int8", help="Compute type for the selected device")
    parser.add_argument("--language", default=None, help="Optional ISO language code")
    args = parser.parse_args()

    audio_path = Path(args.audio_path).expanduser().resolve()
    if not audio_path.exists():
        raise SystemExit(f"Audio file not found: {audio_path}")

    model = WhisperModel(args.model, device=args.device, compute_type=args.compute_type)
    segments, info = model.transcribe(str(audio_path), language=args.language, vad_filter=True)

    segment_rows = []
    text_parts = []
    for segment in segments:
        row = {
            "start": round(segment.start, 2),
            "end": round(segment.end, 2),
            "text": segment.text.strip(),
        }
        if row["text"]:
            segment_rows.append(row)
            text_parts.append(row["text"])

    payload = {
        "text": " ".join(text_parts).strip(),
        "language": info.language,
        "language_probability": round(info.language_probability, 4),
        "duration": round(info.duration, 2),
        "segments": segment_rows,
    }
    print(json.dumps(payload, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
