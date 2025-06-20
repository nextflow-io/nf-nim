#!/usr/bin/env bash
set -e

URL=http://localhost:8000/biology/ipd/rfdiffusion/generate

if [ ! -e 1R42.pdb ]; then curl -O https://files.rcsb.org/download/1R42.pdb; fi

pdb=$(cat 1R42.pdb | grep ^ATOM | head -n 400 | awk '{printf "%s\\n", $0}')

request='{
 "input_pdb": "'"$pdb"'",
 "contigs": "A20-60/0 50-100",
 "hotspot_res": ["A50","A51","A52","A53","A54"],
 "diffusion_steps": 15
}'
curl -H 'Content-Type: application/json' \
     -d "$request" "$URL"
