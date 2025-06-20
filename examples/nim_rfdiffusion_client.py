#!/usr/bin/env python3
import requests
import os
import json
from pathlib import Path

def get_reduced_pdb():
    pdb = Path("1R42.pdb")
    if not pdb.exists():
        pdb.write_text(requests.get(f"https://files.rcsb.org/download/{pdb}").text)
    lines = filter(lambda line: line.startswith("ATOM"), pdb.read_text().split("\n"))
    return "\n".join(list(lines)[:400])

r = requests.post(
    url="http://localhost:8000/biology/ipd/rfdiffusion/generate",
    json={
        "input_pdb": get_reduced_pdb(),
        "contigs": "A20-60/0 50-100",
        "hotspot_res": ["A50","A51","A52","A53","A54"],
        "diffusion_steps": 15,
    },
)
print(r, "Saving to output.pdb:\n", r.text[:200], "...")
Path("output.pdb").write_text(json.loads(r.text)["output_pdb"])
