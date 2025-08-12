# Examples Guide

This guide provides comprehensive examples of using the nf-nim plugin for various bioinformatics workflows with NVIDIA NIM services.

## Quick Start Examples

### Basic RFDiffusion Design

```groovy
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
}

params.pdb_file = "https://files.rcsb.org/download/1R42.pdb"

process downloadPDB {
    output:
    path "input.pdb"
    
    script:
    """
    curl -s ${params.pdb_file} > input.pdb
    """
}

process designProtein {
    executor 'nim'
    publishDir "results", mode: 'copy'
    
    input:
    path pdb_file
    
    output:
    path "designed.pdb"
    path "design_result.json"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Designing protein structure from ${pdb_file}"
    # Outputs are automatically created by the NIM executor
    mv output.pdb designed.pdb
    mv nim_result.json design_result.json
    """
}

workflow {
    pdb = downloadPDB()
    designProtein(pdb)
}
```

### AlphaFold2 Prediction

```groovy
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
}

params.sequence = "MKWVTFISLLFLFSSAYSRGVFRRDAHKSEVAHRFKDLGEENFKALVLIAFAQYLQQCPFEDHVKLVNEVTEFAKTCVAD"

process createFASTA {
    output:
    path "sequence.fasta"
    
    script:
    """
    echo ">protein_sequence" > sequence.fasta
    echo "${params.sequence}" >> sequence.fasta
    """
}

process predictStructure {
    executor 'nim'
    publishDir "predictions", mode: 'copy'
    
    input:
    path fasta_file
    
    output:
    path "predicted.pdb"
    path "prediction_result.json"
    
    script:
    task.ext.nim = "alphafold2"
    """
    echo "Predicting structure for sequence in ${fasta_file}"
    """
}

workflow {
    fasta = createFASTA()
    predictStructure(fasta)
}
```

## Multi-Service Workflows

### Fold-then-Design Pipeline

```groovy
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
}

params {
    sequences = "sequences/*.fasta"
    output_dir = "results"
}

// First fold the proteins
process foldProtein {
    executor 'nim'
    tag "$fasta_file.baseName"
    
    input:
    path fasta_file
    
    output:
    tuple val("${fasta_file.baseName}"), path("folded.pdb")
    
    script:
    task.ext.nim = "alphafold2"
    """
    echo "Folding ${fasta_file}"
    mv output.pdb folded.pdb
    """
}

// Then redesign the folded structures
process redesignProtein {
    executor 'nim'
    tag "$sample_id"
    publishDir "${params.output_dir}/$sample_id", mode: 'copy'
    
    input:
    tuple val(sample_id), path(folded_pdb)
    
    output:
    tuple val(sample_id), path("redesigned.pdb"), path("design_result.json")
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Redesigning ${sample_id}"
    mv output.pdb redesigned.pdb
    mv nim_result.json design_result.json
    """
}

workflow {
    // Read FASTA files
    fasta_ch = Channel.fromPath(params.sequences)
    
    // Fold then redesign
    folded = foldProtein(fasta_ch)
    redesigned = redesignProtein(folded)
    
    // Summary
    redesigned
        .map { sample_id, pdb, json -> sample_id }
        .collect()
        .view { "Completed redesign for: ${it.join(', ')}" }
}
```

### Comparative Analysis Pipeline

```groovy
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
}

params {
    input_pdb = "input.pdb" 
    variants = ["A50G", "L55P", "R60K"]
}

// Create sequence variants
process createVariant {
    input:
    tuple val(variant), path(original_pdb)
    
    output:
    tuple val(variant), path("variant_${variant}.fasta")
    
    script:
    """
    # Extract sequence and apply variant (simplified)
    python apply_variant.py ${original_pdb} ${variant} variant_${variant}.fasta
    """
}

// Predict structures for variants
process predictVariant {
    executor 'nim'
    tag "$variant"
    
    input:
    tuple val(variant), path(fasta_file)
    
    output:
    tuple val(variant), path("predicted_${variant}.pdb")
    
    script:
    task.ext.nim = "alphafold2"
    """
    echo "Predicting structure for variant ${variant}"
    mv output.pdb predicted_${variant}.pdb
    """
}

// Compare with original
process compareStructures {
    publishDir "analysis", mode: 'copy'
    
    input:
    path original_pdb
    tuple val(variant), path(variant_pdb)
    
    output:
    path "${variant}_comparison.txt"
    
    script:
    """
    echo "Comparing ${variant} with original structure" > ${variant}_comparison.txt
    python compare_structures.py ${original_pdb} ${variant_pdb} >> ${variant}_comparison.txt
    """
}

workflow {
    original = file(params.input_pdb)
    
    // Create variants
    variant_ch = Channel.from(params.variants)
        .combine(Channel.of(original))
    
    variant_fastas = createVariant(variant_ch)
    predicted = predictVariant(variant_fastas)
    
    // Compare each variant with original
    comparisons = predicted.combine(Channel.of(original))
        .map { variant, variant_pdb, orig_pdb -> 
            [orig_pdb, [variant, variant_pdb]] 
        }
    
    compareStructures(comparisons)
}
```

## Advanced Configuration Examples

### Multi-Environment Setup

```groovy
// nextflow.config
plugins {
    id 'nf-nim'
}

profiles {
    standard {
        // Uses NVCF_RUN_KEY environment variable
        params.max_parallel = 5
    }
    
    development {
        nim {
            rfdiffusion {
                endpoint = 'http://localhost:8000/biology/ipd/rfdiffusion/generate'
            }
        }
        params {
            diffusion_steps = 5  // Faster for development
            max_parallel = 2
        }
    }
    
    production {
        nim {
            apiKey = "${env.NVIDIA_PROD_KEY}"
        }
        params {
            diffusion_steps = 20  // Higher quality
            max_parallel = 10
        }
    }
    
    enterprise {
        nim {
            rfdiffusion {
                endpoint = 'https://nim.company.com/rfdiffusion'
                apiKey = "${env.ENTERPRISE_API_KEY}"
            }
            alphafold2 {
                endpoint = 'https://nim.company.com/alphafold2'
                apiKey = "${env.ENTERPRISE_API_KEY}"
            }
        }
    }
}

params {
    // Default parameters
    contigs = "A20-60/0 50-100"
    hotspot_res = ["A50", "A51", "A52", "A53", "A54"]
    diffusion_steps = 15
}
```

### Batch Processing with Error Handling

```groovy
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
}

params {
    input_dir = "pdbs/*.pdb"
    batch_size = 10
    max_retries = 3
}

process batchDesign {
    executor 'nim'
    tag "batch_${batch_id}"
    errorStrategy 'retry'
    maxRetries params.max_retries
    
    input:
    tuple val(batch_id), path(pdb_files)
    
    output:
    path "batch_${batch_id}_results/*"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    mkdir -p batch_${batch_id}_results
    
    # Process each file in the batch
    for pdb in ${pdb_files}; do
        echo "Processing \$pdb"
        # NIM will process and create outputs
        # Move results to batch directory
        if [ -f output.pdb ]; then
            mv output.pdb batch_${batch_id}_results/\${pdb%.pdb}_designed.pdb
        fi
        if [ -f nim_result.json ]; then
            mv nim_result.json batch_${batch_id}_results/\${pdb%.pdb}_result.json
        fi
    done
    """
}

process collectResults {
    publishDir "final_results", mode: 'copy'
    
    input:
    path "batch_*"
    
    output:
    path "summary.txt"
    path "all_designs/*"
    
    script:
    """
    mkdir -p all_designs
    
    # Collect all results
    find . -name "*.pdb" -exec cp {} all_designs/ \\;
    find . -name "*.json" -exec cp {} all_designs/ \\;
    
    # Create summary
    echo "Batch Processing Summary" > summary.txt
    echo "========================" >> summary.txt
    echo "Total PDB files: \$(find all_designs -name "*.pdb" | wc -l)" >> summary.txt
    echo "Total JSON files: \$(find all_designs -name "*.json" | wc -l)" >> summary.txt
    echo "Processing completed: \$(date)" >> summary.txt
    """
}

workflow {
    // Read PDB files and batch them
    pdb_ch = Channel.fromPath(params.input_dir)
    
    batched = pdb_ch
        .buffer(size: params.batch_size, remainder: true)
        .map { batch -> 
            ["${UUID.randomUUID().toString()[0..7]}", batch] 
        }
    
    // Process batches
    results = batchDesign(batched)
    
    // Collect all results
    collectResults(results.collect())
}
```

## Real-World Use Cases

### Drug Target Analysis

```groovy
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
}

params {
    target_pdb = "target.pdb"
    binding_sites = ["site1", "site2", "site3"]
    designs_per_site = 5
}

// Extract binding site regions
process extractBindingSite {
    input:
    tuple val(site_id), path(target_pdb)
    
    output:
    tuple val(site_id), path("${site_id}_region.pdb")
    
    script:
    """
    python extract_binding_site.py ${target_pdb} ${site_id} ${site_id}_region.pdb
    """
}

// Generate multiple designs per site
process generateDesigns {
    executor 'nim'
    tag "${site_id}_design_${design_num}"
    
    input:
    tuple val(site_id), path(region_pdb), val(design_num)
    
    output:
    tuple val(site_id), val(design_num), path("design_${site_id}_${design_num}.pdb")
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Generating design ${design_num} for site ${site_id}"
    mv output.pdb design_${site_id}_${design_num}.pdb
    """
}

// Analyze binding affinity (simulation)
process analyzeBinding {
    input:
    tuple val(site_id), val(design_num), path(design_pdb), path(target_pdb)
    
    output:
    tuple val(site_id), val(design_num), path("binding_analysis_${site_id}_${design_num}.txt")
    
    script:
    """
    python analyze_binding.py ${target_pdb} ${design_pdb} > binding_analysis_${site_id}_${design_num}.txt
    """
}

// Rank designs by binding score
process rankDesigns {
    publishDir "rankings", mode: 'copy'
    
    input:
    path "binding_analysis_*.txt"
    
    output:
    path "top_designs.txt"
    
    script:
    """
    python rank_by_binding_score.py binding_analysis_*.txt > top_designs.txt
    """
}

workflow {
    target = file(params.target_pdb)
    
    // Extract binding sites
    sites_ch = Channel.from(params.binding_sites)
        .combine(Channel.of(target))
    
    regions = extractBindingSite(sites_ch)
    
    // Generate multiple designs per site
    designs_ch = regions
        .combine(Channel.from(1..params.designs_per_site))
    
    designs = generateDesigns(designs_ch)
    
    // Analyze binding
    analysis_input = designs
        .combine(Channel.of(target))
        .map { site_id, design_num, design_pdb, target_pdb ->
            [site_id, design_num, design_pdb, target_pdb]
        }
    
    analyses = analyzeBinding(analysis_input)
    
    // Rank all designs
    rankDesigns(analyses.map { it[2] }.collect())
}
```

### Protein Evolution Study

```groovy
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
}

params {
    ancestor_sequence = "ancestral_protein.fasta"
    mutation_rates = [0.01, 0.05, 0.10]
    generations = 10
    population_size = 20
}

// Simulate mutations
process simulateMutations {
    input:
    tuple val(generation), val(mutation_rate), val(individual), path(parent_fasta)
    
    output:
    tuple val(generation), val(mutation_rate), val(individual), path("gen${generation}_mut${mutation_rate}_ind${individual}.fasta")
    
    script:
    """
    python simulate_mutations.py ${parent_fasta} ${mutation_rate} gen${generation}_mut${mutation_rate}_ind${individual}.fasta
    """
}

// Fold evolved sequences
process foldEvolved {
    executor 'nim'
    tag "gen${generation}_mut${mutation_rate}_ind${individual}"
    
    input:
    tuple val(generation), val(mutation_rate), val(individual), path(evolved_fasta)
    
    output:
    tuple val(generation), val(mutation_rate), val(individual), path("folded_gen${generation}_mut${mutation_rate}_ind${individual}.pdb")
    
    script:
    task.ext.nim = "alphafold2"
    """
    echo "Folding evolved sequence: generation ${generation}, mutation rate ${mutation_rate}, individual ${individual}"
    mv output.pdb folded_gen${generation}_mut${mutation_rate}_ind${individual}.pdb
    """
}

// Analyze structural changes
process analyzeEvolution {
    input:
    path reference_pdb
    tuple val(generation), val(mutation_rate), val(individual), path(evolved_pdb)
    
    output:
    tuple val(generation), val(mutation_rate), path("evolution_analysis_gen${generation}_mut${mutation_rate}_ind${individual}.txt")
    
    script:
    """
    python analyze_structural_evolution.py ${reference_pdb} ${evolved_pdb} > evolution_analysis_gen${generation}_mut${mutation_rate}_ind${individual}.txt
    """
}

workflow {
    // Start with ancestor
    ancestor = file(params.ancestor_sequence)
    
    // Generate reference structure
    reference = foldEvolved([[0, 0.0, 0, ancestor]])
        .map { generation, rate, individual, pdb -> pdb }
    
    // Simulate evolution
    mut_rates = Channel.from(params.mutation_rates)
    individuals = Channel.from(1..params.population_size)
    generations = Channel.from(1..params.generations)
    
    // Create initial population
    current_pop = mut_rates
        .combine(individuals)
        .combine(Channel.of(ancestor))
        .map { rate, individual, fasta -> [0, rate, individual, fasta] }
    
    // Evolve through generations
    evolved_structures = simulateMutations(
        generations
            .combine(current_pop)
            .map { gen, gen0, rate, individual, fasta -> [gen, rate, individual, fasta] }
    )
    .map { gen, rate, individual, fasta -> [gen, rate, individual, fasta] }
    | foldEvolved
    
    // Analyze evolution
    reference
        .combine(evolved_structures)
        .map { ref_pdb, gen, rate, individual, evolved_pdb -> 
            [ref_pdb, [gen, rate, individual, evolved_pdb]] 
        }
    | analyzeEvolution
}
```

## Performance Optimization Examples

### Parallel Processing with Resource Management

```groovy
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
}

params {
    input_files = "inputs/*.pdb"
    max_concurrent_nim = 5
}

// Control concurrency for NIM tasks
process designWithConcurrencyControl {
    executor 'nim'
    maxForks params.max_concurrent_nim
    
    input:
    path pdb_file
    
    output:
    path "designed_${pdb_file.baseName}.pdb"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Designing ${pdb_file} (controlled concurrency)"
    mv output.pdb designed_${pdb_file.baseName}.pdb
    """
}

workflow {
    pdb_files = Channel.fromPath(params.input_files)
    
    designs = designWithConcurrencyControl(pdb_files)
    
    designs.subscribe { 
        println "Completed design: ${it}"
    }
}
```

### Conditional Processing

```groovy
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
}

params {
    quality_threshold = 0.8
    skip_low_quality = true
}

// Quality assessment
process assessQuality {
    input:
    path pdb_file
    
    output:
    tuple path(pdb_file), env(QUALITY_SCORE)
    
    script:
    """
    QUALITY_SCORE=\$(python assess_pdb_quality.py ${pdb_file})
    """
}

// Conditional design based on quality
process conditionalDesign {
    executor 'nim'
    
    when:
    quality_score.toFloat() >= params.quality_threshold || !params.skip_low_quality
    
    input:
    tuple path(pdb_file), val(quality_score)
    
    output:
    path "designed_${pdb_file.baseName}.pdb"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Designing ${pdb_file} (quality: ${quality_score})"
    mv output.pdb designed_${pdb_file.baseName}.pdb
    """
}

workflow {
    input_files = Channel.fromPath("inputs/*.pdb")
    
    quality_assessed = assessQuality(input_files)
    
    conditionalDesign(quality_assessed)
}
```

These examples demonstrate the versatility and power of the nf-nim plugin for integrating NVIDIA NIM services into complex bioinformatics workflows. Each example can be adapted and extended based on specific research needs.