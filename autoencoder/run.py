"""
Wrapper for starting uvicorn with timestamped logging.

When using `python -m uvicorn app:app`, uvicorn configures its logging
handlers BEFORE importing app.py, so any LOGGING_CONFIG modifications
inside app.py are too late.

This script modifies LOGGING_CONFIG first, then calls uvicorn.run()
programmatically — guaranteeing timestamps in every log line.
"""
import argparse

import uvicorn
import uvicorn.config

for _fmt in uvicorn.config.LOGGING_CONFIG.get("formatters", {}).values():
    if "fmt" in _fmt:
        _fmt["fmt"] = "%(asctime)s " + _fmt["fmt"]
    _fmt.setdefault("datefmt", "%Y-%m-%d %H:%M:%S")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Histology Autoencoder Service")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    uvicorn.run("app:app", host=args.host, port=args.port)
