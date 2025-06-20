# Nextflow NIM Plugin

A Nextflow plugin for integrating [NVIDIA NIMs (NVIDIA Inference Microservices)](https://developer.nvidia.com/nim) as custom executors for bioinformatics workflows.

## Overview

This plugin provides a generic `nim` executor that can run various NVIDIA NIM services for biological computing, including:

- **RFDiffusion** - Protein structure generation and design
- **AlphaFold2** - Protein structure prediction  
- **ESMFold** - Protein structure prediction
- **DeepVariant** - Genomic variant calling (planned)
- **Fq2Bam** - Sequence alignment (planned)

## Installation

Add the plugin to your `nextflow.config`:

```groovy
plugins {
    id 'nf-nim'
}
```

Or use it directly in your pipeline script:

```groovy
plugins {
    id 'nf-nim@0.1.0'
}
```

## Configuration

The plugin uses NVIDIA's hosted API endpoints by default. You will need to set up authentication with NVIDIA API keys as described in the [NVIDIA NIM documentation](https://developer.nvidia.com/blog/a-simple-guide-to-deploying-generative-ai-with-nvidia-nim/).

Default endpoints:
- **RFDiffusion**: `https://api.nvidia.com/v1/biology/ipd/rfdiffusion/generate`
- **AlphaFold2**: `https://api.nvidia.com/v1/biology/deepmind/alphafold2/predict`
- **ESMFold**: `https://api.nvidia.com/v1/biology/meta/esmfold/predict`

> **Note**: Custom endpoint configuration will be supported in a future release.

## Usage

### Basic Process Configuration

Use the `nim` executor in your processes and specify which NIM service to use with `task.ext.nim`:

```groovy
process myNIMProcess {
    executor 'nim'
    
    input:
    // your inputs
    
    output:
    // your outputs
    
    script:
    task.ext.nim = "rfdiffusion"  // or "alphafold2", "esmfold", etc.
    
    """
    # Your script here - the NIM executor handles the actual API calls
    echo "Running ${task.ext.nim} analysis"
    """
}
```

### RFDiffusion Example

```groovy
process rfdiffusionDesign {
    executor 'nim'
    
    input:
    path pdb_file
    
    output:
    path "output.pdb"

    script:
    task.ext.nim = "rfdiffusion"
    
    """
    echo "Designing protein structure using RFDiffusion"
    """
}
```

Parameters for RFDiffusion can be set in `params`:

```groovy
params.contigs = "A20-60/0 50-100"
params.hotspot_res = ["A50","A51","A52","A53","A54"] 
params.diffusion_steps = 15
```

### AlphaFold2/ESMFold Example

```groovy
process predictStructure {
    executor 'nim'
    
    input:
    val sequence
    
    output:
    path "predicted_structure.pdb"

    script:
    task.ext.nim = "alphafold2"  // or "esmfold"
    
    """
    echo "Predicting structure for sequence of length: \$(echo '${sequence}' | wc -c)"
    """
}
```

### Complete Workflow Example

```groovy
#!/usr/bin/env nextflow

params.pdb_file = "input.pdb"
params.sequence = "MNIFEMLRIDEGLRLKIYKDTEGYY..."

workflow {
    // Structure-based design with RFDiffusion
    if (params.pdb_file) {
        designProtein(file(params.pdb_file))
    }
    
    // Sequence-based prediction with AlphaFold2
    if (params.sequence) {
        predictWithAlphaFold(params.sequence)
        predictWithESMFold(params.sequence)
    }
}

process designProtein {
    executor 'nim'
    
    input:
    path pdb_file
    
    output:
    path "designed.pdb"

    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Designing protein based on ${pdb_file}"
    """
}

process predictWithAlphaFold {
    executor 'nim'
    
    input:
    val sequence
    
    output:
    path "alphafold_prediction.pdb"

    script:
    task.ext.nim = "alphafold2"
    """
    echo "Predicting structure with AlphaFold2"
    """
}

process predictWithESMFold {
    executor 'nim'
    
    input:
    val sequence
    
    output:
    path "esmfold_prediction.pdb"

    script:
    task.ext.nim = "esmfold"
    """
    echo "Predicting structure with ESMFold"
    """
}
```

## Input Requirements

### RFDiffusion
- **Input**: PDB file containing protein structure
- **Parameters**: 
  - `params.contigs` - Contigs specification (default: "A20-60/0 50-100")
  - `params.hotspot_res` - Hotspot residues (default: ["A50","A51","A52","A53","A54"])  
  - `params.diffusion_steps` - Number of diffusion steps (default: 15)

### AlphaFold2/ESMFold
- **Input**: Protein sequence (from FASTA file or `params.sequence`)
- **Parameters**: None required

## Health Checks

Test NIM service availability:

```bash
# RFDiffusion
curl -v https://api.nvidia.com/v1/biology/ipd/rfdiffusion/generate

# AlphaFold2  
curl -v https://api.nvidia.com/v1/biology/deepmind/alphafold2/predict

# ESMFold
curl -v https://api.nvidia.com/v1/biology/meta/esmfold/predict
```

> **Note**: You'll need to include your NVIDIA API key in the request headers for successful authentication.

## Development

### Building

```bash
make assemble
```

### Testing

```bash
make test
```

### Installing Locally

```bash
make install
```

## License

This project is licensed under the Apache License 2.0 - see the [COPYING](COPYING) file for details.

## Contributing

Contributions are welcome! Please see the development guidelines in the source code for more information.