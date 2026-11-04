#!/usr/bin/env nextflow
nextflow.enable.dsl=2

/*
 * Test workflow for the NIM executor with dynamic service routing
 */

params.pdb_url = "https://files.rcsb.org/download/1R42.pdb"

workflow {
    // Test RFDiffusion with custom parameters
    testRFDiffusion()
}

process testRFDiffusion {
    executor 'nim'
    
    output:
    path "output.pdb"
    path "nim_result.json"

    script:
    task.ext.nim = "rfdiffusion"
    task.ext.contigs = "A20-60/0 50-100" 
    task.ext.hotspot_res = ["A50", "A51", "A52", "A53", "A54"]
    task.ext.diffusion_steps = 15
    """
    echo "Testing NIM executor with RFDiffusion service"
    echo "Service: ${task.ext.nim}"
    echo "Contigs: ${task.ext.contigs}"
    echo "Hotspot residues: ${task.ext.hotspot_res}"
    echo "Diffusion steps: ${task.ext.diffusion_steps}"
    """
}