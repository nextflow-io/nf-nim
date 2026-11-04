# RFDiffusion NIM Executor Example

This example demonstrates how to use the RFDiffusion NIM executor with Nextflow to perform protein structure generation using NVIDIA's RFDiffusion NIM service.

## Prerequisites

1. NVIDIA NIM RFDiffusion service running (default: `http://localhost:8000`)
2. Nextflow with the nf-nim plugin installed

## Usage

### Basic Example

Run the example workflow:

```bash
nextflow run rfdiffusion_example.nf -c nextflow.config
```

### Configuration

The RFDiffusion executor can be configured in your `nextflow.config`:

```groovy
nim {
    rfdiffusion {
        endpoint = 'http://localhost:8000/biology/ipd/rfdiffusion/generate'
    }
}

params {
    contigs = "A20-60/0 50-100"
    hotspot_res = ["A50","A51","A52","A53","A54"]
    diffusion_steps = 15
}
```

### Process Definition

Use the `rfdiffusion` executor in your process:

```groovy
process rfdiffusionTask {
    executor 'rfdiffusion'
    
    input:
    path pdb_file
    
    output:
    path "output.pdb"
    
    script:
    """
    echo "Running RFDiffusion on ${pdb_file}"
    """
}
```

## Parameters

- **contigs**: Specifies the protein chain contigs (default: "A20-60/0 50-100")
- **hotspot_res**: List of hotspot residues (default: ["A50","A51","A52","A53","A54"])
- **diffusion_steps**: Number of diffusion steps (default: 15)

## Input/Output

- **Input**: PDB file containing protein structure
- **Output**: Generated PDB file with the new protein structure

## Direct API Testing

You can also test the NIM service directly using the provided scripts:

### Python Client
```bash
python3 nim_rfdiffusion_client.py
```

### Shell Script
```bash
bash nim_rfdiffusion_client.sh
```

Both scripts will download the example 1R42.pdb file and send it to the RFDiffusion NIM service. 