#!/usr/bin/env nextflow

/*
 * Generative Virtual Screening Workflow using NVIDIA BioNeMo NIMs
 * 
 * This workflow implements a complete drug discovery pipeline:
 * 1. Protein Structure Prediction (MSA-Search + OpenFold2)
 * 2. Molecular Generation (GenMol)  
 * 3. Protein-Ligand Docking (DiffDock)
 */

params.target_protein_sequence = "SGFRKMAFPSGKVEGCMVQVTCGTTTLNGLWLDDVVYCPRHVICTSEDMLNPNYEDLLIRKSNHNFLVQAGNVQLRVIGHSMQNCVLKLKVDTANPKTPKYKFVRIQPGQTFSVLACYNGSPSGVYQCAMRPNFTIKGSFLNGSCGSVGFNIDYDCVSFCYMHHMELPTGVHAGTDLEGNFYGPFVDRQTAQAAGTDTTITVNVLAWLYAAVINGDRWFLNRFTTTLNDFNLVAMKYNYEPLTQDHVDILGPLSAQTGIAVLDMCASLKELLQNGMNGRTILGSALLEDEFTPFDVVRQCSGVTFQ"
params.seed_molecule = "C12OC3C(O)C1O.C3O.[*{25-25}]"  // Nirmatrelvir fragment
params.num_molecules = 5
params.num_poses = 10

// MSA Search parameters
params.e_value = 0.0001
params.iterations = 1
params.search_type = "alphafold2"
params.databases = ["Uniref30_2302", "colabfold_envdb_202108", "PDB70_220313"]

// GenMol parameters  
params.temperature = 1
params.noise = 0.2
params.step_size = 4
params.scoring = "QED"

// DiffDock parameters
params.time_divisions = 20
params.num_steps = 18

workflow {
    // Channel with target protein sequence
    ch_protein_sequence = channel.value(params.target_protein_sequence)
    
    // Channel with seed molecule for generation
    ch_seed_molecule = channel.value(params.seed_molecule)
    
    // Step 1: Generate MSA for the protein sequence
    msa_search(ch_protein_sequence)
    
    // Step 2: Fold protein structure using MSA
    protein_folding(ch_protein_sequence, msa_search.out)
    
    // Step 3: Generate optimized molecules
    molecular_generation(ch_seed_molecule)
    
    // Step 4: Dock generated molecules to folded protein
    protein_ligand_docking(
        protein_folding.out,
        molecular_generation.out
    )
    
    // Output final results
    protein_ligand_docking.out.view { "Docking completed for molecules. Results saved to: $it" }
}

process msa_search {
    executor 'local'
    publishDir 'results/msa', mode: 'copy'
    
    input:
    val sequence
    
    output:
    path "msa_alignments.json"
    
    script:
    def baseurl = "https://health.api.nvidia.com/v1/biology/colabfold/"
    def URL = "msa-search/predict"
    """
    request='{
        "sequence": "${sequence}",
        "e_value": ${params.e_value},
        "iterations": ${params.iterations},
        "search_type": "${params.search_type}",
        "output_alignment_formats": ["fasta", "a3m"],
        "databases": ["${params.databases.join('", "')}"]
    }'
    
    curl -s -X POST "${baseurl}${URL}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer \$NVCF_RUN_KEY" \
        -H "NVCF-POLL-SECONDS: 300" \
        -d "\$request" > msa_alignments.json
    
    echo "MSA search completed for sequence length: \$(echo '${sequence}' | wc -c)"
    """
}

process protein_folding {
    executor 'local'
    publishDir 'results/folding', mode: 'copy'
    
    input:
    val sequence
    path msa_file
    
    output:
    path "folded_protein.json"
    
    script:
    def baseurl = "https://health.api.nvidia.com/v1/biology/openfold/"
    def URL = "openfold2/predict-structure-from-msa-and-template"
    """
    # Extract alignments from MSA search results
    alignments=\$(jq -c '.alignments' ${msa_file})
    
    request='{
        "sequence": "${sequence}",
        "use_templates": false,
        "relaxed_prediction": false,
        "alignments": '"\$alignments"'
    }'
    
    curl -s -X POST "${baseurl}${URL}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer \$NVCF_RUN_KEY" \
        -H "NVCF-POLL-SECONDS: 300" \
        -d "\$request" > folded_protein.json
    
    echo "Protein folding completed for sequence"
    """
}

process molecular_generation {
    executor 'local'
    publishDir 'results/generation', mode: 'copy'
    
    input:
    val seed_molecule
    
    output:
    path "generated_molecules.json"
    
    script:
    def baseurl = "https://health.api.nvidia.com/v1/biology/genmol/"
    def URL = "generate"
    """
    request='{
        "smiles": "${seed_molecule}",
        "num_molecules": ${params.num_molecules},
        "temperature": ${params.temperature},
        "noise": ${params.noise},
        "step_size": ${params.step_size},
        "scoring": "${params.scoring}"
    }'
    
    curl -s -X POST "${baseurl}${URL}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer \$NVCF_RUN_KEY" \
        -H "NVCF-POLL-SECONDS: 300" \
        -d "\$request" > generated_molecules.json
        
    echo "Generated \$(jq '.molecules | length' generated_molecules.json) optimized molecules"
    """
}

process protein_ligand_docking {
    executor 'local'  
    publishDir 'results/docking', mode: 'copy'
    
    input:
    path protein_structure_file
    path molecules_file
    
    output:
    path "docking_results.json"
    
    script:
    def baseurl = "https://health.api.nvidia.com/v1/biology/diffdock/"
    def URL = "generate"
    """
    # Extract folded protein structure (PDB format)
    folded_protein=\$(jq -r '.structures_in_ranked_order[0].structure' ${protein_structure_file})
    
    # Extract generated molecules as SMILES strings
    generated_ligands=\$(jq -r '.molecules[].smiles' ${molecules_file} | tr '\n' '\n')
    
    request='{
        "protein": "'"\$folded_protein"'",
        "ligand": "'"\$generated_ligands"'",
        "ligand_file_type": "txt",
        "num_poses": ${params.num_poses},
        "time_divisions": ${params.time_divisions},
        "num_steps": ${params.num_steps}
    }'
    
    curl -s -X POST "${baseurl}${URL}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer \$NVCF_RUN_KEY" \
        -H "NVCF-POLL-SECONDS: 300" \
        -d "\$request" > docking_results.json
        
    echo "Protein-ligand docking completed for \$(echo "\$generated_ligands" | wc -l) molecules"
    """
}

/*
 * Optional: Extract and save individual results
 */
process extract_results {
    executor 'local'
    publishDir 'results/final', mode: 'copy'
    
    input:
    path docking_file
    path molecules_file
    path protein_file
    
    output:
    path "final_results.txt"
    path "top_poses.pdb"
    
    script:
    """
    echo "=== Generative Virtual Screening Results ===" > final_results.txt
    echo "" >> final_results.txt
    
    echo "Generated Molecules with QED Scores:" >> final_results.txt
    jq -r '.molecules[] | "SMILES: \\(.smiles), QED Score: \\(.score)"' ${molecules_file} >> final_results.txt
    echo "" >> final_results.txt
    
    echo "Docking Status:" >> final_results.txt  
    jq -r '.status[]' ${docking_file} >> final_results.txt
    echo "" >> final_results.txt
    
    echo "Top confidence poses saved to top_poses.pdb" >> final_results.txt
    
    # Extract top pose for each molecule
    jq -r '.ligand_positions[][] | select(. != null)' ${docking_file} | head -n 1 > top_poses.pdb
    """
} 