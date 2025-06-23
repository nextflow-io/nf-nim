#!/usr/bin/env nextflow

/*
 * Example Nextflow script using containers for RFDiffusion and OpenFold
 * Demonstrates protein design and structure prediction using containerized tools
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
    // Download PDB file
    pdb_ch = channel.fromPath(params.pdb_file)
    
    // Example 1: RFDiffusion protein design
    rfdiffusion_results = rfdiffusion(pdb_ch, params.contigs, params.hotspot_res, params.diffusion_steps)
    
    // Example 2: OpenFold protein structure prediction
    sequences_ch = channel.from([
        params.sequence, 
        params.sequence_7WJ0_A, 
        params.sequence_7WBN_A, 
        params.sequence_7ONG_A
    ])
    
    alphafold_results = alphafold(sequences_ch)
}

process rfdiffusion {
    container 'nvcr.io/nvidia/rfdiffusion:latest'
    
    publishDir 'results/rfdiffusion', mode: 'copy'
    
    input:
    path pdb_file
    val contigs
    val hotspot_res
    val diffusion_steps

    output:
    path "rfdiffusion_output.pdb"
    path "rfdiffusion_log.txt"

    script:
    def hotspot_str = hotspot_res.join(',')
    """
    # Run RFDiffusion with the provided parameters
    python /opt/RFdiffusion/scripts/run_inference.py \\
        --config-path=/opt/RFdiffusion/config/inference \\
        --config-name=base \\
        inference.input_pdb=${pdb_file} \\
        'contigmap.contigs=[${contigs}]' \\
        'ppi.hotspot_res=[${hotspot_str}]' \\
        diffuser.T=${diffusion_steps} \\
        inference.output_prefix=rfdiffusion_output \\
        inference.num_designs=1 \\
        denoiser.noise_scale_ca=1 \\
        denoiser.noise_scale_frame=1 \\
        > rfdiffusion_log.txt 
    """
}

process alphafold {
    container 'deepmind/alphafold:latest'
    
    publishDir 'results/openfold', mode: 'copy'
    
    input:
    val sequence

    output:
    path "openfold_output_*.pdb"
    path "openfold_log_*.txt"

    script:
    def seq_hash = sequence.take(10).replaceAll(/[^A-Za-z0-9]/, '').toLowerCase()
    """
    # Create FASTA file for the sequence
    echo ">${seq_hash}" > input.fasta
    echo "${sequence}" >> input.fasta
    
    # Run OpenFold/AlphaFold prediction
    # Note: This is a simplified example - actual OpenFold setup may require additional steps
    python /app/alphafold/run_alphafold.py \\
        --fasta_paths=input.fasta \\
        --max_template_date=2022-01-01 \\
        --model_preset=monomer \\
        --db_preset=reduced_dbs \\
        --output_dir=. \\
        --use_gpu_relax=false \\
        --logtostderr \\
        > openfold_log_${seq_hash}.txt 
    """
}

// Alternative process using ESMFold (lighter weight option)
process esmfold {
    container 'pytorch/pytorch:latest'
    
    publishDir 'results/esmfold', mode: 'copy'
    
    input:
    val sequence

    output:
    path "esmfold_output_*.pdb"

    script:
    def seq_hash = sequence.take(10).replaceAll(/[^A-Za-z0-9]/, '').toLowerCase()
    """
    # Install ESMFold if not available
    pip install fair-esm torch biotite
    
    # Create Python script for ESMFold prediction
    cat << 'EOF' > predict_structure.py
import torch
import esm
from biotite.structure.io import save_structure
from biotite.structure import AtomArray
import sys

# Load ESMFold model
model = esm.pretrained.esmfold_v1()
model = model.eval()

# Predict structure
sequence = "${sequence}"
output = model.infer_pdb(sequence)

# Save to file
with open("esmfold_output_${seq_hash}.pdb", "w") as f:
    f.write(output)

print(f"Structure prediction completed for sequence: {sequence[:50]}...")
EOF

    python predict_structure.py
    """
} 
