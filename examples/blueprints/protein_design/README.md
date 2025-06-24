# De Novo Protein Binder Design Workflow

This Nextflow workflow implements a complete protein binder design pipeline using NVIDIA Inference Microservices (NIMs). The workflow follows the computational biology architecture for designing novel protein binders that can interact with target proteins.

## Workflow Architecture

The workflow implements a six-step protein design pipeline:

1. **AlphaFold2**: Predict target protein structure from amino acid sequence
2. **RFDiffusion**: Generate protein backbones for binder design using diffusion models
3. **ProteinMPNN**: Generate amino acid sequences from backbone structures
4. **Binder-Target Pairing**: Create pairs of designed binders with target protein
5. **AlphaFold2-Multimer**: Predict complex structures of binder-target pairs
6. **Binding Assessment**: Evaluate binding quality using pLDDT scores and rank results

## Prerequisites

### Software Requirements
- Nextflow (≥21.04.0)
- Docker or Singularity (optional, for containerized execution)
- curl and jq (for API calls)
- Python 3.8+ (for data processing scripts)

### NVIDIA API Access
- NVIDIA API key for accessing NIMs services
- Set the `NVIDIA_API_KEY` environment variable before running the workflow

```bash
export NVIDIA_API_KEY="your_api_key_here"
```

## Quick Start

### Basic Execution
```bash
# Run with default parameters (5PTN example)
nextflow run protein-binder-design.nf -c protein-binder-design.config

# Run with custom target sequence
nextflow run protein-binder-design.nf \
    --target_sequence "YOUR_PROTEIN_SEQUENCE_HERE" \
    --contigs "A1-50/0 60-120" \
    --hotspot_res '["A25", "A26", "A30"]'
```

### With Profiles
```bash
# Run with Docker
nextflow run protein-binder-design.nf -profile docker

# Run on SLURM cluster
nextflow run protein-binder-design.nf -profile slurm

# Run with Singularity
nextflow run protein-binder-design.nf -profile singularity
```

## Configuration Parameters

### Primary Parameters
- `target_sequence`: Target protein sequence for binder design
- `contigs`: RFDiffusion contigs specification (e.g., "A1-25/0 70-100")
- `hotspot_res`: List of hotspot residues for binder design
- `pairs_to_process`: Number of binder-target pairs to process (default: 5)

### RFDiffusion Parameters
- `diffusion_steps`: Number of diffusion steps (default: 15)

### ProteinMPNN Parameters
- `num_seq_per_target`: Number of sequences to generate per backbone (default: 20)
- `sampling_temp`: Sampling temperature for sequence generation (default: [0.1])
- `ca_only`: Use CA-only model (default: false)
- `use_soluble_model`: Use soluble protein model (default: false)

### AlphaFold2 Parameters
- `algorithm`: MSA algorithm - "mmseqs2" or "jackhmmer" (default: "mmseqs2")

## Example Configurations

### Small Target (Fast Execution)
```bash
nextflow run protein-binder-design.nf \
    --target_sequence "MGILPSPGMPALLSLVSLLSVLLMGCVAETGTQCVNLTTRTQL" \
    --contigs "A1-20/0 30-60" \
    --hotspot_res '["A10", "A12", "A15"]' \
    --num_seq_per_target 10 \
    --pairs_to_process 3
```

### Large Target (Resource Intensive)
```bash
nextflow run protein-binder-design.nf \
    --target_sequence "VERY_LONG_PROTEIN_SEQUENCE..." \
    --contigs "A50-150/0 80-160" \
    --hotspot_res '["A75", "A80", "A85", "A90"]' \
    --num_seq_per_target 50 \
    --pairs_to_process 10 \
    --max_parallel_jobs 2
```

## Output Structure

The workflow generates organized outputs in the `results/` directory:

```
results/
├── alphafold2_structures/          # Target protein structures
│   ├── target_structure.pdb
│   └── alphafold2_output.json
├── rfdiffusion_backbones/          # Generated binder backbones
│   ├── rfdiffusion_backbone.pdb
│   └── rfdiffusion_output.json
├── proteinmpnn_sequences/          # Generated binder sequences
│   ├── proteinmpnn_sequences.fasta
│   └── proteinmpnn_output.json
├── alphafold2_multimer/            # Complex structure predictions
│   ├── multimer_predictions_*.pdb
│   └── multimer_metadata_*.json
├── binding_assessment/             # Binding quality analysis
│   ├── binding_assessment.json
│   └── ranked_binders.txt
├── timeline.html                   # Execution timeline
├── report.html                     # Workflow report
├── trace.txt                       # Execution trace
└── dag.svg                         # Workflow DAG
```

## Understanding Results

### Binding Assessment
The workflow ranks binder-target complexes by average pLDDT score:
- **pLDDT > 90**: Very high confidence
- **pLDDT 70-90**: Confident
- **pLDDT 50-70**: Low confidence
- **pLDDT < 50**: Very low confidence

### Key Output Files
- `ranked_binders.txt`: Tab-separated ranking of all binder-target complexes
- `binding_assessment.json`: Detailed assessment with pLDDT scores
- `multimer_predictions_*.pdb`: 3D structures of binder-target complexes

## Troubleshooting

### Common Issues

1. **API Key Missing**
   ```
   Error: Authorization failed
   Solution: Set NVIDIA_API_KEY environment variable
   ```

2. **Memory Issues**
   ```
   Error: Process exceeded memory limit
   Solution: Increase memory allocation in config or reduce batch size
   ```

3. **Long Runtime**
   ```
   Issue: AlphaFold2 predictions taking too long
   Solution: Use mmseqs2 algorithm, reduce sequence length, or use faster hardware
   ```

### Performance Optimization

- Use `mmseqs2` algorithm for faster MSA generation
- Reduce `num_seq_per_target` for faster execution
- Limit `pairs_to_process` for resource-constrained environments
- Use appropriate execution profiles (docker, slurm, etc.)

## Resource Requirements

### Minimum Requirements
- 4 CPU cores
- 16 GB RAM
- 50 GB storage
- Internet connection for NIM API access

### Recommended Requirements
- 8+ CPU cores
- 32+ GB RAM
- 100+ GB storage
- High-speed internet connection

### Estimated Runtime
- Small protein (50-100 AA): 30-60 minutes
- Medium protein (100-300 AA): 1-3 hours
- Large protein (300+ AA): 3-8 hours

*Runtimes depend on hardware, network speed, and NIM service load*

## Citation

If you use this workflow in your research, please cite:
- Nextflow: https://www.nextflow.io/
- NVIDIA NIMs: https://developer.nvidia.com/nim
- AlphaFold2: https://doi.org/10.1038/s41586-021-03819-2
- RFDiffusion: https://doi.org/10.1038/s41586-023-06415-8
- ProteinMPNN: https://doi.org/10.1126/science.add2187

## License

This workflow is provided under the MIT License. Please check individual NIM service licensing terms.

## Support

For workflow issues, please check:
1. Nextflow documentation: https://www.nextflow.io/docs/latest/
2. NVIDIA NIM documentation: https://docs.nvidia.com/nim/
3. Workflow parameters and configuration examples above 