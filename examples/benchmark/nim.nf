#!/usr/bin/env nextflow

/*
 * Example Nextflow script using the generic NIM executor
 * Demonstrates multiple NVIDIA NIM services (RFDiffusion, AlphaFold2, ESMFold)
 */

params.pdb_file        = "https://files.rcsb.org/download/1R42.pdb"
// PCSK9
// https://www.rcsb.org/structure/2P4E
// params.pdb_file        = "https://files.rcsb.org/download/2P4E.pdb"
params.sequence        = "MNIFEMLRIDEGLRLKIYKDTEGYYTIGIGHLLTKSPSLNAAKSELDKAIGRNTNGVITKDEAEKLFNQDVDAAVRGILRNAKLKPVYDSLDAVRRAALINMVFQMGETGVAGFTNSLRMLQQKRWDEAAVNLAKSRWYNQTPNRAKRVITTFRTGTWDAYKNL"

// OpenFold example sequences
params.sequence_7WJ0_A = "SGSMKTAISLPDETFDRVSR RASELGMSRSEFFTKAAQRYLHELDAQLLLTGQ"
params.sequence_7WBN_A = "GGSKENEISHHAKEIERLQKEIERHKQSIKKLKQSEQSNPPPNPEGTRQARRNRRRRWRERQRQKENEISHHAKEIERLQKEIERHKQSIKKLKQSEC"
params.sequence_7ONG_A = "GSHMNGLTYAVGGYDGTGYNTHLNSVEAYDPERNEWSLVAPLSTRR SGVGVAVLNGLIYAVGGYDGTGYNTHLNSVEAYDPERNEWSLVAPLSTRR SGVGVAVLNGLIYAVGGYDGTGYNTHLNSVEAYDPERNEWSLVAPLSTRR SGVGVAVLNGLIYAVGGYDGTGYNTHLNSVEAYDPERNEWSLVAPLSTRR SGVGVAVLNGLIYAVGGYDGTGYNTHLNSVEAYDPERNEWSLVAPL"

// RFDiffusion parameters
params.contigs         = "A20-60/0 50-100"
params.hotspot_res     = ["A50", "A51", "A52", "A53", "A54"]
params.diffusion_steps = 15

workflow {
    // Example 1: RFDiffusion protein design
    curl_rfdiffusion([file(params.pdb_file, exists: true), params.contigs, params.hotspot_res, params.diffusion_steps])
    
    // Example 2: OpenFold protein structure prediction
    ch_sequences = channel.from([params.sequence, params.sequence_7WJ0_A, params.sequence_7WBN_A, params.sequence_7ONG_A])

    curl_openfold(ch_sequences)
}

process nimRFDiffusion {
    executor 'nim'
    ext nim: 'rfdiffusion'
    ext contigs: contigs
    ext hotspot_res: hotspot_res
    ext diffusion_steps: diffusion_steps

    input:
    tuple path(pdb_file), val(contigs), val(hotspot_res), val(diffusion_steps)

    output:
    path "output.pdb"

    script:
    """
    # The NIM executor will handle the actual API call to RFDiffusion
    # Input parameters are automatically passed from params
    echo "Running RFDiffusion protein design on ${pdb_file}"
    echo "Using contigs: ${params.contigs}"
    echo "Hotspot residues: ${params.hotspot_res}"
    echo "Diffusion steps: ${params.diffusion_steps}"
    """
}

process curl_rfdiffusion {
    executor 'local'

    input:
    tuple path(pdb_file), val(contigs), val(hotspot_res), val(diffusion_steps)

    output:
    path "output.pdb"

    script:
    def baseurl="https://health.api.nvidia.com/v1/biology/ipd/"
    def URL="rfdiffusion/generate"
    """
    pdb=\$(cat ${pdb_file} | grep ^ATOM | head -n 400 | awk '{printf "%s\\n", \$0}')
    request='{
    "input_pdb": "'"\$pdb"'",
    "contigs": "${contigs}",
    "hotspot_res": "${hotspot_res}",
    "diffusion_steps": "${diffusion_steps}"
    }'
    curl -H 'Content-Type: application/json' \
        -H "Authorization: Bearer \$NVCF_RUN_KEY" \
        -H "nvcf-poll-seconds: 300" \
        -d "\$request" "$baseurl$URL" > output.json

    jq -r '.output_pdb' output.json > output.pdb
    """
}

process curl_openfold {
    executor 'local'

    input:
    val sequence

    output:
    path "openfold_output.json"

    script:
    def baseurl = "https://health.api.nvidia.com/v1/biology/openfold/"
    def URL = "openfold2/predict-structure-from-msa-and-template"
    """
    request='{
        "sequence": "${sequence}"
    }'
    
    curl -s -X POST "${baseurl}${URL}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer \$NVCF_RUN_KEY" \
        -H "NVCF-POLL-SECONDS: 300" \
        -d "\$request" > openfold_output.json
    """
}

// msasearch
// evo2-40b
