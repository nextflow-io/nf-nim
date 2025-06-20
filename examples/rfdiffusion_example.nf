#!/usr/bin/env nextflow

/*
 * Example Nextflow script using the generic NIM executor
 * Demonstrates multiple NVIDIA NIM services (RFDiffusion, AlphaFold2, ESMFold)
 */

params.pdb_file = "1R42.pdb"
params.sequence = "MNIFEMLRIDEGLRLKIYKDTEGYYTIGIGHLLTKSPSLNAAKSELDKAIGRNTNGVITKDEAEKLFNQDVDAAVRGILRNAKLKPVYDSLDAVRRAALINMVFQMGETGVAGFTNSLRMLQQKRWDEAAVNLAKSRWYNQTPNRAKRVITTFRTGTWDAYKNL"

// RFDiffusion parameters
params.contigs = "A20-60/0 50-100"
params.hotspot_res = ["A50","A51","A52","A53","A54"]
params.diffusion_steps = 15

workflow {
    // Example 1: RFDiffusion protein design
    if (params.pdb_file && file(params.pdb_file).exists() || params.pdb_file == "1R42.pdb") {
        if (!file(params.pdb_file).exists()) {
            downloadPdb()
            rfdiffusionTask(downloadPdb.out)
        } else {
            rfdiffusionTask(file(params.pdb_file))
        }
    }
    
    // Example 2: AlphaFold2 structure prediction
    if (params.sequence) {
        alphafold2Task(params.sequence)
    }
    
    // Example 3: ESMFold structure prediction 
    if (params.sequence) {
        esmfoldTask(params.sequence)
    }
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
    executor 'nim'
    
    input:
    path pdb_file
    
    output:
    path "output.pdb"

    script:
    // Specify which NIM service to use
    task.ext.nim = "rfdiffusion"
    
    """
    # The NIM executor will handle the actual API call to RFDiffusion
    # Input parameters are automatically passed from params
    echo "Running RFDiffusion protein design on ${pdb_file}"
    echo "Using contigs: ${params.contigs}"
    echo "Hotspot residues: ${params.hotspot_res}"
    echo "Diffusion steps: ${params.diffusion_steps}"
    """
}

process alphafold2Task {
    executor 'nim'
    
    input:
    val sequence
    
    output:
    path "predicted_structure.pdb"

    script:
    task.ext.nim = "alphafold2"
    
    """
    # The NIM executor will handle the AlphaFold2 API call
    echo "Running AlphaFold2 structure prediction"
    echo "Sequence length: \$(echo '${sequence}' | wc -c)"
    """
}

process esmfoldTask {
    executor 'nim'
    
    input:
    val sequence
    
    output:
    path "predicted_structure.pdb"

    script:
    task.ext.nim = "esmfold"
    
    """
    # The NIM executor will handle the ESMFold API call  
    echo "Running ESMFold structure prediction"
    echo "Sequence length: \$(echo '${sequence}' | wc -c)"
    """
}

// Health check example:
// curl -v https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate 