---
name: add-content-processor
description: Scaffold a new Python content processor module for MemoryVault. Use when adding a new processing capability to the content-processor service (yt-dlp downloader, RSS parser, web scraper, page archiver, etc.).
---

# Add Content Processor

You are adding a new Python processing module to the MemoryVault content-processor service.

## Where Processors Live

```
content-processor/
├── src/
│   └── <processor_name>/
│       ├── __init__.py
│       └── processor.py
├── tests/
│   └── <processor_name>/
│       ├── __init__.py
│       └── test_processor.py
└── requirements.txt
```

## Steps

### 1. Write the failing test first

Create `content-processor/tests/<processor_name>/test_processor.py`:

```python
import pytest
from unittest.mock import patch, MagicMock
from src.<processor_name>.processor import <ProcessorName>

class Test<ProcessorName>:

    def test_process_returns_result_on_valid_input(self):
        processor = <ProcessorName>()
        result = processor.process("<valid input>")
        assert result is not None
        assert result["status"] == "success"

    def test_process_raises_on_empty_input(self):
        processor = <ProcessorName>()
        with pytest.raises(ValueError):
            processor.process("")

    def test_process_handles_network_error(self):
        processor = <ProcessorName>()
        with patch("<dependency>", side_effect=ConnectionError("network down")):
            result = processor.process("<input>")
        assert result["status"] == "error"
        assert "network" in result["error"].lower()
```

Run: `cd content-processor && python -m pytest tests/<processor_name>/ -v`
Expected: FAIL — module does not exist.

### 2. Create the processor module

Create `content-processor/src/<processor_name>/__init__.py` (empty).

Create `content-processor/src/<processor_name>/processor.py`:

```python
import logging
from typing import Any

logger = logging.getLogger(__name__)

class <ProcessorName>:
    """<What this processor does, its input, and its output.>"""

    def process(self, input: str) -> dict[str, Any]:
        if not input:
            raise ValueError("Input cannot be empty")
        try:
            logger.info("Processing: %s", input)
            # --- implementation here ---
            return {"status": "success", "result": None}
        except Exception as e:
            logger.error("Processing failed: %s", str(e), exc_info=True)
            return {"status": "error", "error": str(e)}
```

Run: `python -m pytest tests/<processor_name>/ -v` — Expected: PASS.

### 3. Add dependencies to requirements.txt if needed

```
<new-package>==<version>  # used by <processor_name>
```

### 4. For I/O-bound processors, make it async

Refer to the `async-python-patterns` skill for guidance.

### 5. Commit

```bash
git add content-processor/src/<processor_name>/ content-processor/tests/<processor_name>/
git commit -m "feat: add <ProcessorName> content processor with tests"
```
