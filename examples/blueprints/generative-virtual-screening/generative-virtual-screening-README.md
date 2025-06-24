# Generative Virtual Screening Workflow

This Nextflow workflow implements a complete generative virtual screening pipeline for drug discovery using NVIDIA BioNeMo NIMs (NVIDIA Inference Microservices).

## Workflow Overview

The pipeline follows the generative virtual screening architecture shown in the workflow diagram:

1. **Protein Structure Prediction**
   - MSA-Search (MMSeqs2) for multiple sequence alignment
   - OpenFold2 for protein structure folding

2. **Molecular Generation**
   - GenMol for generating optimized drug-like molecules
   - QED scoring for drug-likeness assessment

3. **Protein-Ligand Docking**
   - DiffDock for predicting binding poses
   - Confidence scoring for pose ranking

## Prerequisites

### NVIDIA Cloud Functions (NVCF) Setup

1. **Get NVCF Run Key**: Obtain your NVCF run key from [NVIDIA NGC](https://ngc.nvidia.com/)
2. **Set Environment Variable**: 
   ```bash
   export NVCF_RUN_KEY="your-run-key-here"
   ```

### Required Software

- Nextflow (>=21.10.0)
- curl
- jq (for JSON processing)

## Usage

### Basic Execution

```bash
# Run with default parameters (SARS-CoV-2 main protease + Nirmatrelvir)
nextflow run generative-virtual-screening.nf -c generative-virtual-screening.config

# Run with custom parameters
nextflow run generative-virtual-screening.nf \
    --target_protein_sequence "YOUR_PROTEIN_SEQUENCE" \
    --seed_molecule "YOUR_SEED_SMILES" \
    --num_molecules 10 \
    --outdir results/my_screening
```

### Custom Parameters

#### Protein Parameters
- `--target_protein_sequence`: Target protein amino acid sequence
- `--e_value`: E-value threshold for MSA search (default: 0.0001)
- `--databases`: MSA search databases (default: Uniref30, ColabFold, PDB70)

#### Molecular Generation Parameters
- `--seed_molecule`: Seed molecule in SMILES or SAFE format
- `--num_molecules`: Number of molecules to generate (default: 5)
- `--temperature`: Generation temperature (default: 1.0)
- `--noise`: Noise level for generation (default: 0.2)
- `--scoring`: Scoring function ("QED", "SA", "logP", etc.)

#### Docking Parameters
- `--num_poses`: Number of poses per molecule (default: 10)
- `--time_divisions`: Time divisions for diffusion (default: 20)
- `--num_steps`: Number of diffusion steps (default: 18)

### Execution Profiles

#### Local Execution
```bash
nextflow run generative-virtual-screening.nf -profile standard
```

#### SLURM Cluster
```bash
nextflow run generative-virtual-screening.nf -profile slurm
```

#### AWS Batch
```bash
nextflow run generative-virtual-screening.nf -profile aws
```

## Output Structure

```
results/
├── msa/
│   └── msa_alignments.json          # MSA search results
├── folding/
│   └── folded_protein.json          # Protein structure prediction
├── generation/
│   └── generated_molecules.json     # Generated molecules with scores
├── docking/
│   └── docking_results.json         # Docking poses and confidence scores
├── final/
│   ├── final_results.txt            # Summary of results
│   └── top_poses.pdb                # Top confidence poses
├── report.html                      # Nextflow execution report
├── timeline.html                    # Execution timeline
├── trace.txt                        # Process trace
└── dag.svg                          # Workflow DAG
```

## Example Usage Scenarios

### 1. COVID-19 Drug Discovery
```bash
# Use SARS-CoV-2 main protease (default)
nextflow run generative-virtual-screening.nf \
    --seed_molecule "CC(C)CC(C(=O)N1C[C@H](CN(C1=O)C(=O)C(F)(F)F)C(=O)NC(CC2=CC=CC=C2)C(=O)C(=O)N)NC(=O)C3=CC=C(C=C3)C(=O)C" \
    --num_molecules 20
```

### 2. Custom Protein Target
```bash
# Use your own protein sequence
nextflow run generative-virtual-screening.nf \
    --target_protein_sequence "MGDVEKGKKIFIMKCSQCHTVLHGLFGRQHH..." \
    --seed_molecule "CCO" \
    --scoring "SA"
```

### 3. Fragment-based Drug Design
```bash
# Start from a fragment library
nextflow run generative-virtual-screening.nf \
    --seed_molecule "c1ccccc1" \
    --num_molecules 50 \
    --temperature 1.5
```

## Monitoring and Troubleshooting

### Check Process Status
```bash
# View execution report
open results/report.html

# Check trace file
tail -f results/trace.txt
```

### Common Issues

1. **NVCF Authentication**: Ensure `NVCF_RUN_KEY` is set correctly
2. **API Rate Limits**: NIMs may have rate limits; adjust `maxRetries` if needed
3. **Large Sequences**: MSA search may timeout for very long sequences
4. **Memory Requirements**: Increase memory for large molecular libraries

### Logs and Debugging
```bash
# Run with debug information
nextflow run generative-virtual-screening.nf -c generative-virtual-screening.config -with-trace -with-report

# Resume from checkpoint
nextflow run generative-virtual-screening.nf -resume
```

## Customization

### Adding New Scoring Functions
Modify the `molecular_generation` process to include additional scoring metrics:

```groovy
process molecular_generation {
    // ... existing code ...
    script:
    """
    request='{
        "smiles": "${seed_molecule}",
        "num_molecules": ${params.num_molecules},
        "scoring": ["QED", "SA", "logP"]  // Multiple scoring functions
    }'
    """
}
```

### Parallel Molecule Processing
For large-scale screening, consider splitting molecules into batches:

```groovy
// Add to workflow block
ch_molecules = molecular_generation.out
    .splitJson()
    .map { it.molecules }
    .flatten()
    .collate(10)  // Process in batches of 10

protein_ligand_docking(protein_folding.out, ch_molecules)
```

## Performance Optimization

- **Parallel Processing**: Use `-profile slurm` for cluster execution
- **Caching**: Nextflow automatically caches results; use `-resume` for restarts
- **Resource Allocation**: Adjust memory/CPU in the configuration file
- **Batch Size**: Optimize molecule batch sizes for your infrastructure

## References

- [NVIDIA BioNeMo NIMs](https://docs.nvidia.com/nim/bionemo/latest/)
- [Nextflow Documentation](https://www.nextflow.io/docs/latest/)
- [OpenFold2 Paper](https://www.nature.com/articles/s41586-021-03819-2)
- [DiffDock Paper](https://arxiv.org/abs/2210.01776)
- [GenMol Paper](https://arxiv.org/abs/2304.03850) 