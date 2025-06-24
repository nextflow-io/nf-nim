#!/usr/bin/env nextflow

/*
 * De Novo Protein Binder Design Workflow using NVIDIA NIMs
 * 
 * This workflow follows the protein design architecture:
 * 1. AlphaFold2: Predict target protein structure from sequence
 * 2. RFDiffusion: Generate protein backbones for binder design
 * 3. ProteinMPNN: Generate sequences from backbone structures
 * 4. AlphaFold2-multimer: Predict complex structures and assess binding
 */

// Default parameters for protein design workflow
params.target_sequence = "NITEEFYQSTCSAVSKGYLSALRTGWYTSVITIELSNIKKIKCNGTDAKIKLIKQELDKYKNAVTELQLLMQSTPATNNQARGSGSGRSLGFLLGVGSAIASGVAVSKVLHLEGEVNKIKSALLSTNKAVVSLSNGVSVLTSKVLDLKNYIDKQLLPIVNKQSCSIPNIETVIEFQQKNNRLLEITREFSVNAGVTTPVSTYMLTNSELLSLINDMPITNDQKKLMSNNVQIVRQQSYSIMSIIKEEVLAYVVQLPLYGVIDTPCWKLHTSPLCTTNTKEGSNICLTRTDRGWYCDNAGSVSFFPQAETCKVQSNRVFCDTMNSLTLPSEVNLCNVDIFNPKYDCKIMTSKTDVSSSVITSLGAIVSCYGKTKCTASNKNRGIIKTFSNGCDYVSNKGVDTVSVGNTLYYVNKQEGKSLYVKGEPIINFYDPLVFPSDQFDASISQVNEKINQSLAFIRKSDELLSAIGGYIPEAPRDGQAYVRKDGEWVLLSTFLGGLVPRGSHHHHHH"

// RFDiffusion parameters
params.contigs = "A1-25/0 70-100"
params.hotspot_res = ["A14", "A15", "A17", "A18"]
params.diffusion_steps = 15

// ProteinMPNN parameters  
params.ca_only = false
params.use_soluble_model = false
params.num_seq_per_target = 20
params.sampling_temp = [0.1]

// AlphaFold2 parameters
params.algorithm = "mmseqs2"

// Number of binder-target pairs to process through multimer prediction
params.pairs_to_process = 5

workflow {
    // Input channel with target sequence
    ch_target_sequence = channel.of(params.target_sequence)
    
    // Step 1: Predict target protein structure with AlphaFold2
    alphafold2_structure(ch_target_sequence)
    
    // Step 2: Generate protein backbones with RFDiffusion
    rfdiffusion_binder_design(
        alphafold2_structure.out.structure,
        params.contigs,
        params.hotspot_res,
        params.diffusion_steps
    )
    
    // Step 3: Generate sequences from backbones with ProteinMPNN
    proteinmpnn_sequence_design(
        rfdiffusion_binder_design.out.backbone,
        params.ca_only,
        params.use_soluble_model,
        params.num_seq_per_target,
        params.sampling_temp
    )
    
    // Step 4: Create binder-target pairs and predict complex structures
    create_binder_target_pairs(
        proteinmpnn_sequence_design.out.sequences,
        ch_target_sequence
    )
    
    // Step 5: Predict complex structures with AlphaFold2-multimer
    alphafold2_multimer_prediction(
        create_binder_target_pairs.out.pairs.take(params.pairs_to_process)
    )
    
    // Step 6: Assess binding quality and rank results
    assess_binding_quality(
        alphafold2_multimer_prediction.out.complex_structures
    )
}

/*
 * Process 1: AlphaFold2 - Predict target protein structure from sequence
 */
process alphafold2_structure {
    tag "$sequence"
    publishDir 'results/alphafold2_structures', mode: 'copy'
    
    input:
    val sequence
    
    output:
    path "target_structure.pdb", emit: structure
    path "alphafold2_output.json", emit: metadata
    
    script:
    def baseurl = "https://health.api.nvidia.com/v1/biology/alphafold2/"
    def endpoint = "predict-structure-from-sequence"
    """
    # Call AlphaFold2 NIM for structure prediction
    request='{
        "sequence": "${sequence}",
        "algorithm": "${params.algorithm}"
    }'
    
    curl -s -X POST "${baseurl}${endpoint}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer \$NVIDIA_API_KEY" \
        -H "poll-seconds: 900" \
        -d "\$request" > alphafold2_output.json
    
    # Extract the first structure prediction from the response
    jq -r '.[0]' alphafold2_output.json > target_structure.pdb
    
    echo "AlphaFold2 structure prediction completed for sequence length: \$(echo -n '${sequence}' | wc -c)"
    """
}

/*
 * Process 2: RFDiffusion - Generate protein backbones for binder design
 */
process rfdiffusion_binder_design {
    tag "RFDiffusion_${contigs}"
    publishDir 'results/rfdiffusion_backbones', mode: 'copy'
    
    input:
    path input_pdb
    val contigs
    val hotspot_res
    val diffusion_steps
    
    output:
    path "rfdiffusion_backbone.pdb", emit: backbone
    path "rfdiffusion_output.json", emit: metadata
    
    script:
    def baseurl = "https://health.api.nvidia.com/v1/biology/ipd/"
    def endpoint = "rfdiffusion/generate"
    """
    # Read and format the input PDB structure
    pdb_content=\$(cat ${input_pdb} | grep ^ATOM | awk '{printf "%s\\n", \$0}')
    
    # Prepare RFDiffusion request
    request='{
        "input_pdb": "'"\$pdb_content"'",
        "contigs": "${contigs}",
        "hotspot_res": ${hotspot_res.toString().replace('[', '[').replace(']', ']')},
        "diffusion_steps": ${diffusion_steps}
    }'
    
    curl -s -X POST "${baseurl}${endpoint}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer \$NVIDIA_API_KEY" \
        -H "poll-seconds: 300" \
        -d "\$request" > rfdiffusion_output.json
    
    # Extract the generated backbone structure
    jq -r '.output_pdb' rfdiffusion_output.json > rfdiffusion_backbone.pdb
    
    echo "RFDiffusion backbone generation completed with contigs: ${contigs}"
    """
}

/*
 * Process 3: ProteinMPNN - Generate sequences from backbone structures
 */
process proteinmpnn_sequence_design {
    tag "ProteinMPNN"
    publishDir 'results/proteinmpnn_sequences', mode: 'copy'
    
    input:
    path backbone_pdb
    val ca_only
    val use_soluble_model
    val num_seq_per_target
    val sampling_temp
    
    output:
    path "proteinmpnn_sequences.fasta", emit: sequences
    path "proteinmpnn_output.json", emit: metadata
    
    script:
    def baseurl = "https://health.api.nvidia.com/v1/biology/ipd/"
    def endpoint = "proteinmpnn/predict"
    """
    # Read backbone structure
    backbone_content=\$(cat ${backbone_pdb})
    
    # Prepare ProteinMPNN request
    request='{
        "input_pdb": "'"\$backbone_content"'",
        "input_pdb_chains": ["A"],
        "ca_only": ${ca_only},
        "use_soluble_model": ${use_soluble_model},
        "num_seq_per_target": ${num_seq_per_target},
        "sampling_temp": ${sampling_temp.toString()}
    }'
    
    curl -s -X POST "${baseurl}${endpoint}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer \$NVIDIA_API_KEY" \
        -H "poll-seconds: 300" \
        -d "\$request" > proteinmpnn_output.json
    
    # Extract sequences from FASTA output
    jq -r '.mfasta' proteinmpnn_output.json > proteinmpnn_sequences.fasta
    
    echo "ProteinMPNN generated ${num_seq_per_target} sequences"
    """
}

/*
 * Process 4: Create binder-target pairs for multimer prediction
 */
process create_binder_target_pairs {
    tag "CreatePairs"
    
    input:
    path sequences_fasta
    val target_sequence
    
    output:
    path "binder_target_pairs.json", emit: pairs
    
    script:
    """
    #!/usr/bin/env python3
    import json
    import re
    
    # Read binder sequences from FASTA file
    binder_sequences = []
    with open('${sequences_fasta}', 'r') as f:
        content = f.read()
        # Extract sequences (skip header lines starting with >)
        sequences = [line.strip() for line in content.split('\\n') if line.strip() and not line.startswith('>')]
        # Remove empty sequences and take every other line (sequences, not headers)
        binder_sequences = [seq for seq in sequences if len(seq) > 10]
    
    # Create binder-target pairs
    target_seq = "${target_sequence}"
    pairs = []
    
    for i, binder_seq in enumerate(binder_sequences):
        pair = {
            "id": f"pair_{i+1}",
            "binder_sequence": binder_seq,
            "target_sequence": target_seq,
            "sequences": [binder_seq, target_seq]
        }
        pairs.append(pair)
    
    # Save pairs to JSON file
    with open('binder_target_pairs.json', 'w') as f:
        json.dump(pairs, f, indent=2)
    
    print(f"Created {len(pairs)} binder-target pairs")
    """
}

/*
 * Process 5: AlphaFold2-multimer - Predict complex structures
 */
process alphafold2_multimer_prediction {
    tag "Multimer"
    publishDir 'results/alphafold2_multimer', mode: 'copy'
    
    input:
    path pairs_json
    
    output:
    path "multimer_predictions_*.pdb", emit: complex_structures
    path "multimer_metadata_*.json", emit: metadata
    
    script:
    def baseurl = "https://health.api.nvidia.com/v1/biology/alphafold2/"
    def endpoint = "multimer/predict-structure-from-sequences"
    """
    #!/usr/bin/env python3
    import json
    import subprocess
    import sys
    
    # Read binder-target pairs
    with open('${pairs_json}', 'r') as f:
        pairs = json.load(f)
    
    # Process each pair (limit to first few for demonstration)
    for i, pair in enumerate(pairs[:${params.pairs_to_process}]):
        pair_id = pair['id']
        sequences = pair['sequences']
        
        # Prepare multimer request
        request = {
            "sequences": sequences,
            "selected_models": [1]
        }
        
        # Make API call
        cmd = [
            'curl', '-s', '-X', 'POST', 
            f"${baseurl}${endpoint}",
            '-H', 'Content-Type: application/json',
            '-H', f'Authorization: Bearer {\$NVIDIA_API_KEY}',
            '-H', 'poll-seconds: 900',
            '-d', json.dumps(request)
        ]
        
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, check=True)
            response = result.stdout
            
            # Save response metadata
            with open(f'multimer_metadata_{pair_id}.json', 'w') as f:
                f.write(response)
            
            # Extract structure (assuming response is a JSON with structure data)
            response_data = json.loads(response)
            if isinstance(response_data, list) and len(response_data) > 0:
                with open(f'multimer_predictions_{pair_id}.pdb', 'w') as f:
                    f.write(response_data[0])
                print(f"Processed multimer prediction for {pair_id}")
            else:
                print(f"Warning: Unexpected response format for {pair_id}")
                with open(f'multimer_predictions_{pair_id}.pdb', 'w') as f:
                    f.write("# No structure data available\\n")
                    
        except Exception as e:
            print(f"Error processing {pair_id}: {e}")
            with open(f'multimer_predictions_{pair_id}.pdb', 'w') as f:
                f.write(f"# Error: {e}\\n")
    """
}

/*
 * Process 6: Assess binding quality using pLDDT scores
 */
process assess_binding_quality {
    tag "AssessBinding"
    publishDir 'results/binding_assessment', mode: 'copy'
    
    input:
    path "multimer_*.pdb"
    
    output:
    path "binding_assessment.json", emit: assessment
    path "ranked_binders.txt", emit: ranked_results
    
    script:
    """
    #!/usr/bin/env python3
    import glob
    import json
    import os
    
    def calculate_average_pLDDT(pdb_content):
        \"\"\"Calculate average pLDDT over all CA atoms\"\"\"
        total_pLDDT = 0.0
        atom_count = 0
        
        for line in pdb_content.split('\\n'):
            if line.startswith('ATOM'):
                atom_name = line[12:16].strip()
                if atom_name == 'CA':  # Only consider CA atoms
                    try:
                        pLDDT = float(line[60:66].strip())
                        total_pLDDT += pLDDT
                        atom_count += 1
                    except ValueError:
                        continue
        
        return total_pLDDT / atom_count if atom_count > 0 else 0.0
    
    # Process all multimer prediction files
    pdb_files = glob.glob('multimer_*.pdb')
    results = []
    
    for pdb_file in pdb_files:
        try:
            with open(pdb_file, 'r') as f:
                pdb_content = f.read()
            
            # Skip empty or error files
            if pdb_content.startswith('#') or len(pdb_content.strip()) < 100:
                continue
                
            avg_pLDDT = calculate_average_pLDDT(pdb_content)
            
            result = {
                'filename': pdb_file,
                'pair_id': os.path.basename(pdb_file).replace('multimer_predictions_', '').replace('.pdb', ''),
                'average_pLDDT': round(avg_pLDDT, 2)
            }
            results.append(result)
            
        except Exception as e:
            print(f"Error processing {pdb_file}: {e}")
    
    # Sort by pLDDT (higher is better)
    results.sort(key=lambda x: x['average_pLDDT'], reverse=True)
    
    # Save assessment results
    assessment = {
        'total_complexes': len(results),
        'average_pLDDT_overall': round(sum(r['average_pLDDT'] for r in results) / len(results), 2) if results else 0,
        'results': results
    }
    
    with open('binding_assessment.json', 'w') as f:
        json.dump(assessment, f, indent=2)
    
    # Create ranked results file
    with open('ranked_binders.txt', 'w') as f:
        f.write("Rank\\tPair_ID\\tAverage_pLDDT\\tFilename\\n")
        for i, result in enumerate(results, 1):
            f.write(f"{i}\\t{result['pair_id']}\\t{result['average_pLDDT']}\\t{result['filename']}\\n")
    
    print(f"Processed {len(results)} complex structures")
    if results:
        print(f"Best binder: {results[0]['pair_id']} with pLDDT: {results[0]['average_pLDDT']}")
    """
} 