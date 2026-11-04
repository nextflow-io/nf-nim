# Usage Guide

This guide covers how to use the nf-nim plugin effectively in your Nextflow workflows for NVIDIA NIM services.

## Basic Usage

### Simple RFDiffusion Process

```groovy
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
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
    echo "Designing protein structure from ${pdb_file}"
    """
}

workflow {
    designProtein(file(params.pdb_file))
}
```

### Service Selection

Use `task.ext.nim` to specify which NIM service to use:

```groovy
process alphafoldPrediction {
    executor 'nim'
    
    input:
    path fasta_file
    
    output:
    path "prediction.pdb"
    
    script:
    task.ext.nim = "alphafold2"  // or "openfold"
    """
    echo "Folding protein from ${fasta_file}"
    """
}

process proteinDesign {
    executor 'nim'
    
    input:
    path template_pdb
    
    output:
    path "designed.pdb"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Designing new protein based on ${template_pdb}"
    """
}
```

## Available NIM Services

### RFDiffusion

**Purpose**: Protein structure generation and design

**Input**: PDB file containing protein structure
**Output**: Generated PDB file with new/modified structure

```groovy
process rfdiffusionDesign {
    executor 'nim'
    
    input:
    path pdb_file
    
    output:
    path "output.pdb"
    path "nim_result.json"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Processing ${pdb_file} with RFDiffusion"
    """
}
```

**Parameters** (set in `params`):
- `contigs`: Contigs specification (default: `"A20-60/0 50-100"`)
- `hotspot_res`: Hotspot residues (default: `["A50","A51","A52","A53","A54"]`)
- `diffusion_steps`: Number of diffusion steps (default: `15`)

### AlphaFold2

**Purpose**: Protein structure prediction from sequence

**Input**: FASTA file with protein sequence
**Output**: Predicted PDB structure

```groovy
process alphafoldFolding {
    executor 'nim'
    
    input:
    path fasta_file
    
    output:
    path "folded.pdb"
    
    script:
    task.ext.nim = "alphafold2"
    """
    echo "Folding sequence from ${fasta_file}"
    """
}
```

### OpenFold

**Purpose**: Open-source protein folding

**Input**: FASTA file with protein sequence
**Output**: Predicted PDB structure

```groovy
process openfoldPrediction {
    executor 'nim'
    
    input:
    path fasta_file
    
    output:
    path "predicted.pdb"
    
    script:
    task.ext.nim = "openfold"
    """
    echo "Predicting structure with OpenFold"
    """
}
```

## Parameter Configuration

### Global Parameters

Set parameters in your configuration or command line:

```groovy
params {
    // Input files
    pdb_file = "input.pdb"
    fasta_file = "sequence.fasta"
    
    // RFDiffusion parameters
    contigs = "A20-60/0 50-100"
    hotspot_res = ["A50", "A51", "A52", "A53", "A54"]
    diffusion_steps = 15
    
    // Output directory
    output_dir = "./results"
}
```

### Process-Specific Parameters

Override parameters for specific processes:

```groovy
process fastDesign {
    executor 'nim'
    
    script:
    task.ext.nim = "rfdiffusion"
    task.ext.diffusion_steps = 5  // Override global setting
    """
    echo "Fast design with ${task.ext.diffusion_steps} steps"
    """
}
```

## Workflow Patterns

### Sequential Workflow

Chain multiple NIM services:

```groovy
workflow sequentialDesign {
    take:
    input_fasta
    
    main:
    // First fold the protein
    folded = alphafoldFolding(input_fasta)
    
    // Then redesign it
    designed = rfdiffusionDesign(folded)
    
    emit:
    designed
}
```

### Parallel Processing

Process multiple inputs in parallel:

```groovy
workflow parallelDesign {
    take:
    pdb_files
    
    main:
    // Process all PDB files in parallel
    designed = rfdiffusionDesign(pdb_files)
    
    // Collect results
    all_designs = designed.collect()
    
    emit:
    all_designs
}
```

### Mixed Executors

Combine NIM executor with other executors:

```groovy
process preprocessPDB {
    executor 'local'
    
    input:
    path raw_pdb
    
    output:
    path "cleaned.pdb"
    
    script:
    """
    # Clean and validate PDB file
    python clean_pdb.py ${raw_pdb} cleaned.pdb
    """
}

process designProtein {
    executor 'nim'
    
    input:
    path clean_pdb
    
    output:
    path "designed.pdb"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Designing from ${clean_pdb}"
    """
}

workflow {
    raw_pdb = file(params.pdb_file)
    clean_pdb = preprocessPDB(raw_pdb)
    designed = designProtein(clean_pdb)
}
```

## Error Handling

### Retry Logic

Handle temporary failures:

```groovy
process robustDesign {
    executor 'nim'
    errorStrategy 'retry'
    maxRetries 3
    
    input:
    path pdb_file
    
    output:
    path "output.pdb"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Attempting design of ${pdb_file}"
    """
}
```

### Conditional Execution

Skip processes based on conditions:

```groovy
process conditionalDesign {
    executor 'nim'
    
    when:
    params.enable_design && pdb_file.size() > 0
    
    input:
    path pdb_file
    
    output:
    path "designed.pdb"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Conditional design of ${pdb_file}"
    """
}
```

## Output Files

### Standard Outputs

All NIM processes generate:

- **Primary output**: The main result file (PDB, JSON, etc.)
- **nim_result.json**: Detailed API response and metadata
- **Log files**: Process execution logs

### Output Organization

```groovy
process organizedDesign {
    executor 'nim'
    publishDir "${params.output_dir}/designs", mode: 'copy'
    
    input:
    path pdb_file
    
    output:
    path "designed_${pdb_file.baseName}.pdb", emit: structure
    path "result_${pdb_file.baseName}.json", emit: metadata
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    # Output files are automatically created by NIM executor
    # Rename for clarity
    mv output.pdb designed_${pdb_file.baseName}.pdb
    mv nim_result.json result_${pdb_file.baseName}.json
    """
}
```

## Performance Considerations

### Batch Processing

Process multiple inputs efficiently:

```groovy
workflow batchDesign {
    take:
    pdb_channel
    
    main:
    // Group inputs for batch processing
    batched = pdb_channel
        .buffer(size: 10)
        .map { batch -> 
            [ "batch_${batch.size()}", batch ] 
        }
    
    // Process batches
    results = processBatch(batched)
    
    emit:
    results.flatten()
}

process processBatch {
    executor 'nim'
    
    input:
    tuple val(batch_id), path(pdb_files)
    
    output:
    path "*.pdb"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Processing batch ${batch_id} with ${pdb_files.size()} files"
    # Process files individually or as batch depending on service
    """
}
```

### Resource Management

Configure appropriate resources:

```groovy
process resourceAwareDesign {
    executor 'nim'
    
    // NIM processes typically don't need local resources
    // since computation happens on NVIDIA's servers
    memory '1 GB'
    cpus 1
    
    input:
    path pdb_file
    
    output:
    path "designed.pdb"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Designing ${pdb_file}"
    """
}
```

## Debugging

### Enable Debug Mode

```bash
NXF_DEBUG=1 nextflow run your-pipeline.nf
```

### Check API Communication

```groovy
process debugDesign {
    executor 'nim'
    
    input:
    path pdb_file
    
    output:
    path "designed.pdb"
    path "debug.log"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Debug information:"
    echo "PDB file: ${pdb_file}"
    echo "File size: \$(wc -c < ${pdb_file})"
    echo "Service: ${task.ext.nim}"
    
    # NIM executor will handle the actual API call
    echo "API call completed" > debug.log
    """
}
```

## Best Practices

### 1. Input Validation

Always validate inputs before NIM processing:

```groovy
process validatePDB {
    input:
    path pdb_file
    
    output:
    path pdb_file
    
    when:
    pdb_file.size() > 0
    
    script:
    """
    # Validate PDB format
    grep -q "^ATOM" ${pdb_file} || exit 1
    """
}
```

### 2. Meaningful Names

Use descriptive process and variable names:

```groovy
process rfdiffusionProteinDesign {  // Clear purpose
    executor 'nim'
    
    input:
    path template_structure
    
    output:
    path "designed_protein.pdb"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Designing protein based on template: ${template_structure}"
    """
}
```

### 3. Configuration Management

Keep configuration separate from code:

```groovy
// nextflow.config
params {
    design_params {
        contigs = "A20-60/0 50-100"
        diffusion_steps = 15
        hotspot_res = ["A50", "A51", "A52"]
    }
}
```

### 4. Error Recovery

Implement robust error handling:

```groovy
process robustNIMProcess {
    executor 'nim'
    errorStrategy 'finish'  // Continue with other tasks
    
    input:
    path input_file
    
    output:
    path "output.pdb", optional: true
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Processing ${input_file}"
    # Output will be created by NIM executor or marked as failed
    """
}
```

## Advanced Configuration

### Custom Output Filenames

The NIM executor supports dynamic output filename configuration:

```groovy
process customOutputs {
    executor 'nim'
    
    input:
    path input_pdb
    
    output:
    path "${task.name}_designed.pdb"
    path "${task.name}_response.json"
    
    script:
    task.ext.nim = "rfdiffusion"
    // Configure custom output filenames
    task.ext.outputFile = "${task.name}_designed.pdb"
    task.ext.resultFile = "${task.name}_response.json"
    """
    echo "Designing protein with custom filenames"
    """
}
```

**Available Variables**:
- `${task.name}` - Nextflow task name
- `${serviceName}` - NIM service name (e.g., 'rfdiffusion')
- `${task.hash}` - Task hash identifier

**Default Filenames**:
- Main output: `output.pdb`
- API response: `nim_result.json`

### Process Logging

The NIM executor automatically creates standard Nextflow log files in each task's work directory:

- `.command.out` - Standard output from API calls and processing
- `.command.err` - Error messages and warnings
- `.command.log` - Combined log with debug information
- `.command.sh` - Generated script (shows NIM executor usage)
- `.exitcode` - Task exit status

These files provide detailed information for debugging and monitoring NIM API interactions.

### Error Handling Best Practices

Configure robust error handling for NIM processes:

```groovy
process robustNIM {
    executor 'nim'
    errorStrategy 'retry'
    maxRetries 3
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Robust NIM processing with retries"
    """
}
```

This usage guide provides the foundation for effectively integrating NVIDIA NIM services into your Nextflow workflows using the nf-nim plugin.