`restoration-input.jpg` is the real Samoyed photo used by the official PyTorch Hub examples:
https://github.com/pytorch/hub/blob/master/images/dog.jpg

Downloaded unchanged on 2026-10-24 from:
https://raw.githubusercontent.com/pytorch/hub/master/images/dog.jpg

SHA-256: `f3f87bb8ab3c26c7ecfd3ac60421d7f32b0503d1d6c5baf8bac42ed93d86351a`

This fixture is packaged only in the instrumentation test APK, never in the application APK.
The integration test creates 403x317 and 1920x1080 fixture inputs from this photo, then restores
each size patch by patch with the actual MoCE-IR model on Android CPU in both overlap modes.
It is a smoke test for runtime compatibility, not a restoration-quality reference or benchmark.
