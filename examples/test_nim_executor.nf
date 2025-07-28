#!/usr/bin/env nextflow
nextflow.enable.dsl=2

/*
 * Test workflow for the NIM executor
 */

params.pdb_file = "https://files.rcsb.org/download/1R42.pdb"

workflow {
    // Test RFDiffusion with NIM executor
    nimRFDiffusion(file(params.pdb_file))
}

process nimRFDiffusion {
    executor 'nim'
    
    input:
    path pdb_file
    
    output:
    path "output.pdb", optional: true
    path "nim_result.json", optional: true

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
    echo "PDB file: ${pdb_file}"
    """
}