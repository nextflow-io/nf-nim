#!/bin/bash
#
# usage
#   (1) Open a bash shell
#   (2) Run the command "export NVCF_RUN_KEY=<your key>"
#   (3) Copy-paste this code into an empty file named example.sh 
#       in the current working directory.
#   (4) Run the command "source example.sh"
#
# notes
#   - the sequence is 7WBN_A
#

# Variables
KEY="${NVCF_RUN_KEY}"
URL="https://health.api.nvidia.com/v1/biology/openfold/openfold2/predict-structure-from-msa-and-template"
SEQUENCE="GGSKENEISHHAKEIERLQKEIERHKQSIKKLKQSEQSNPPPNPEGTRQ\
ARRNRRRRWRERQRQKENEISHHAKEIERLQKEIERHKQSIKKLKQSEC"

# Prepare the JSON data
DATA="{\"sequence\": \"$SEQUENCE\"}"
echo "DATA=${DATA}"

# Make the request
echo "Submitting request...the response should arrive in less than a 1 minute"
POLL_SECONDS=300
curl -s -X POST "${URL}" \
    -H "content-type: application/json" \
    -H "Authorization: Bearer ${KEY}" \
    -H "NVCF-POLL-SECONDS: ${POLL_SECONDS}" \
    -d "${DATA}"  &> output.json
