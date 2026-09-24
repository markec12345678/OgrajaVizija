"""Nastavitve backenda. Vse je privzeto LOKALNO; nic ni obvezno placljivo."""
import os
from dataclasses import dataclass, field


@dataclass
class Settings:
    host: str = os.getenv("OVIZ_HOST", "0.0.0.0")
    port: int = int(os.getenv("OVIZ_PORT", "8787"))

    # mapa za projekte in modele
    data_dir: str = os.getenv("OVIZ_DATA", os.path.join(os.path.expanduser("~"), ".ograjavizija"))
    models_dir: str = os.getenv("OVIZ_MODELS", os.path.join(os.path.expanduser("~"), ".ograjavizija", "models"))

    # 🟢 GEOMETRY  - brez AI (telefon naredi vse, backend samo shrani/servi)
    # 🟢 LAMA      - LaMa odstranitev (CPU)
    # 🟡 FLUX2_KLEIN_4B     - Apache-2.0, ~8-13 GB VRAM
    # 🟡 QWEN_EDIT_2511     - Apache-2.0, 8-16 GB VRAM
    # 🟡 SDXL_INPAINT_IPA   - starejsa pot (samo za primerjavo kakovosti!)
    # 🔴 REPLICATE / FAL    - zunanji placjivi API; SAMO ce sam nastavis kljuc
    default_provider: str = os.getenv("OVIZ_PROVIDER", "LAMA")

    # Opcije za difuzijo
    diffusion_device: str = os.getenv("OVIZ_DEVICE", "cuda")
    diffusion_dtype: str = os.getenv("OVIZ_DTYPE", "bf16")
    diffusion_steps: int = int(os.getenv("OVIZ_STEPS", "8"))
    diffusion_strength: float = float(os.getenv("OVIZ_STRENGTH", "0.35"))
    max_work_edge: int = int(os.getenv("OVIZ_MAX_EDGE", "1600"))

    # Roksal inquiry inbox: privzeto izklopljen; odklene ga eksplicitni bearer token.
    inquiry_token: str = os.getenv("OVIZ_INQUIRY_TOKEN", "")
    inquiry_max_bytes: int = int(os.getenv("OVIZ_INQUIRY_MAX_BYTES", str(25 * 1024 * 1024)))

    # 🔴 samo ce ZELIS zunanji API (nikoli privzeto)
    replicate_api_token: str = os.getenv("REPLICATE_API_TOKEN", "")
    fal_key: str = os.getenv("FAL_KEY", "")

    allow_origins: list = field(default_factory=lambda: ["*"])


settings = Settings()
