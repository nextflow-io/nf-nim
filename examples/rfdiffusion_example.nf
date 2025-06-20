#!/usr/bin/env nextflow

/*
 * Example Nextflow script using the generic NIM executor
 * Demonstrates multiple NVIDIA NIM services (RFDiffusion, AlphaFold2, ESMFold)
 */

params.pdb_file        = "https://files.rcsb.org/download/1R42.pdb"
params.sequence        = "MNIFEMLRIDEGLRLKIYKDTEGYYTIGIGHLLTKSPSLNAAKSELDKAIGRNTNGVITKDEAEKLFNQDVDAAVRGILRNAKLKPVYDSLDAVRRAALINMVFQMGETGVAGFTNSLRMLQQKRWDEAAVNLAKSRWYNQTPNRAKRVITTFRTGTWDAYKNL"

// RFDiffusion parameters
params.contigs         = "A20-60/0 50-100"
params.hotspot_res     = ["A50", "A51", "A52", "A53", "A54"]
params.diffusion_steps = 15

workflow {
    // Example 1: RFDiffusion protein design
    nimRFDiffusion(file(params.pdb_file, exists: true))
}

process nimRFDiffusion {
    executor 'nim'
    ext nim: 'rfdiffusion'

    input:
    path pdb_file

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