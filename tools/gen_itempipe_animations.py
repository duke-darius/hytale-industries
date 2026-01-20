#!/usr/bin/env python3
"""
Generate per-side-config .blockyanim files for Item Pipes.

Each direction can be Default, Extract, or None. We emit one animation per
6-direction combination (3^6 combos), naming by the packed sideConfig value
(2 bits per dir, 0=Default, 1=Extract, 2=None) as used by ItemPipeBlockState.

Visibility rules per direction:
  Extract: Indicator=V, Bars=V, Energy_Passive=H, Energy_Off=H, Energy_Active=V
  Default: Indicator=V, Bars=V, Energy_Passive=V, Energy_Off=H, Energy_Active=H
  None   : Indicator=H, Bars=H, Energy_Passive=H, Energy_Off=H, Energy_Active=H

Groups follow the naming: <DIR>_Indicator, <DIR>_Bars, <DIR>_Energy_Passive,
<DIR>_Energy_Off, <DIR>_Energy_Active where DIR in
["North","South","West","East","Up","Down"].
"""

from __future__ import annotations

import itertools
import json
from pathlib import Path
from typing import Dict, List, Iterable, Any

DIRS: List[str] = ["North", "South", "West", "East", "Up", "Down"]
DIR_TO_MODEL = {
    "North": "South",
    "South": "North",
    "West": "East",
    "East": "West",
    "Up": "Up",
    "Down": "Down",
}

# For this test we scale groups to zero instead of toggling visibility.
VIS_RULES = {
    "Extract": {
        "Indicator": (1, 1, 1),
        "Bars": (1, 1, 1),
        "Energy_Passive": (0, 0, 0),
        "Energy_Off": (0, 0, 0),
        "Energy_Active": (1, 1, 1),
    },
    "Default": {
        "Indicator": (1, 1, 1),
        "Bars": (1, 1, 1),
        "Energy_Passive": (1, 1, 1),
        "Energy_Off": (0, 0, 0),
        "Energy_Active": (0, 0, 0),
    },
    "None": {
        "Indicator": (0, 0, 0),
        "Bars": (0, 0, 0),
        "Energy_Passive": (0, 0, 0),
        "Energy_Off": (0, 0, 0),
        "Energy_Active": (0, 0, 0),
    },
}


def iter_node_names(node: Dict[str, Any]) -> Iterable[str]:
    yield node.get("name")
    for child in node.get("children") or []:
        if isinstance(child, dict):
            yield from iter_node_names(child)


def gather_prefixed_names(model: Dict[str, Any], prefix: str) -> List[str]:
    names: List[str] = []
    for root in model.get("nodes") or []:
        if not isinstance(root, dict):
            continue
        for name in iter_node_names(root):
            if isinstance(name, str) and name.startswith(prefix):
                names.append(name)
    return names


def make_animation(config: Dict[str, str], model: Dict[str, Any]) -> Dict:
    node_anims = {}
    for direction, state in config.items():
        rules = VIS_RULES[state]
        for suffix, scale in rules.items():
            model_dir = DIR_TO_MODEL[direction]
            names_for_suffix = gather_prefixed_names(model, f"{model_dir}_{suffix}")
            for name in names_for_suffix:
                node_anims[name] = {
                    "position": [],
                    "orientation": [],
                    "scale": [],
                    "shapeStretch": [
                        {
                            "time": 1,
                            "delta": {
                                "x": scale[0],
                                "y": scale[1],
                                "z": scale[2],
                            },
                            "interpolationType": "step",
                        }
                    ],
                    "shapeVisible": [],
                    "shapeUvOffset": []
                }
    return {
        "formatVersion": 1,
        "duration": 1,
        "holdLastKeyframe": True,
        "nodeAnimations": node_anims,
    }


def encode_side_config(config: Dict[str, str]) -> int:
    raw = 0
    for i, d in enumerate(DIRS):
        v = {"Default": 0, "Extract": 1, "None": 2}[config[d]]
        raw |= (v << (i * 2))
    return raw


def main():
    model_path = Path("src/main/resources/Common/Blocks/HytaleIndustries_ItemPipes/HytaleIndustries_ItemPipes_Pipe.blockymodel")
    model = json.loads(model_path.read_text(encoding="utf-8"))

    out_dir = Path("src/main/resources/Common/Blocks/Animations/ItemPipes")
    out_dir.mkdir(parents=True, exist_ok=True)

    total = 0
    for states in itertools.product(["Default", "Extract", "None"], repeat=6):
        cfg = dict(zip(DIRS, states))
        raw = encode_side_config(cfg)
        anim = make_animation(cfg, model)
        fname = f"HytaleIndustries_ItemPipe_State_{raw:03d}.blockyanim"
        out_path = out_dir / fname
        out_path.write_text(json.dumps(anim, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        total += 1

    print(f"Wrote {total} animations to {out_dir}")


if __name__ == "__main__":
    main()
