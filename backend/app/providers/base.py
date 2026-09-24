from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

import numpy as np


@dataclass
class FinalizeRequest:
    scene: np.ndarray        # HxWx4 original (nikoli se ne spreminja)
    composite: np.ndarray    # HxWx4 geometricno sestavljeno (referenca ze na mestu)
    mask: np.ndarray         # HxW uint8
    product: np.ndarray      # HxWx4 izrez referencne ograje
    prompt: str
    strength: float
    steps: int
    seed: int


@dataclass
class FinalizeResult:
    image: np.ndarray        # HxWx4
    provider: str
    elapsed_ms: int
    notes: str = ""


class Provider(Protocol):
    name: str
    tier: str  # "green" | "yellow" | "red"

    def available(self) -> bool: ...
    def finalize(self, req: FinalizeRequest) -> FinalizeResult: ...
    def remove_object(self, image: np.ndarray, mask: np.ndarray) -> np.ndarray: ...
