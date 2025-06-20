#!/usr/bin/env nextflow

/*
 * Example Nextflow script using the RFDiffusion NIM executor
 */

params.pdb_file = "1R42.pdb"
params.contigs = "A20-60/0 50-100"
params.hotspot_res = ["A50","A51","A52","A53","A54"]
params.diffusion_steps = 15

workflow {
    // Download example PDB file if it doesn't exist
    if (!file(params.pdb_file).exists()) {
        downloadPdb()
    }
    
    // Run RFDiffusion using the NIM executor
    rfdiffusionTask(file(params.pdb_file))
}

process downloadPdb {
    output:
    path "1R42.pdb"
    
    script:
    """
    curl -O https://files.rcsb.org/download/1R42.pdb
    """
}

process rfdiffusionTask {
    executor 'rfdiffusion'
    
    input:
    path pdb_file
    
    output:
    path "output.pdb"
    
    script:
    """
    # The RFDiffusion executor will handle the actual NIM API call
    # This script block is mainly for compatibility
    echo "Running RFDiffusion on ${pdb_file}"
    """
} 