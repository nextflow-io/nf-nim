# Configuration Guide

This guide covers all configuration options available for the nf-nim plugin.

## Basic Configuration

Add the plugin to your `nextflow.config`:

```groovy
plugins {
    id 'nf-nim'
}

// Basic NIM configuration
nim {
    apiKey = 'your-api-key-here'
}
```

## Authentication Configuration

### Global API Key

Set a global API key that applies to all NIM services:

```groovy
nim {
    apiKey = 'nvapi-your-global-key'
}
```

### Service-Specific API Keys

Configure different API keys for different services:

```groovy
nim {
    rfdiffusion {
        apiKey = 'nvapi-rfdiffusion-key'
    }
    alphafold2 {
        apiKey = 'nvapi-alphafold2-key'
    }
    openfold {
        apiKey = 'nvapi-openfold-key'
    }
}
```

### Priority Order

API keys are resolved in this order:
1. Service-specific configuration (`nim.service.apiKey`)
2. Global configuration (`nim.apiKey`)
3. Environment variable (`NVCF_RUN_KEY`)

## Endpoint Configuration

### Default Endpoints

The plugin uses these default NVIDIA Health API endpoints:

- **RFDiffusion**: `https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate`
- **AlphaFold2**: `https://health.api.nvidia.com/v1/biology/deepmind/alphafold2`
- **OpenFold**: `https://health.api.nvidia.com/v1/biology/openfold`

### Custom Endpoints

Override default endpoints for self-hosted or custom NIM instances:

```groovy
nim {
    rfdiffusion {
        endpoint = 'http://your-nim-server:8080/biology/ipd/rfdiffusion/generate'
        apiKey = 'your-custom-key'
    }
}
```

### Local Development

For local NIM development servers:

```groovy
nim {
    rfdiffusion {
        endpoint = 'http://localhost:8000/biology/ipd/rfdiffusion/generate'
        apiKey = 'dev-key'  // or omit for no authentication
    }
}
```

## Process Configuration

### Basic Process Setup

```groovy
process rfdiffusionTask {
    executor 'nim'
    
    input:
    path pdb_file
    
    output:
    path "output.pdb"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Processing ${pdb_file} with RFDiffusion"
    """
}
```

### Service Selection

Use `task.ext.nim` to specify which NIM service to use:

```groovy
process alphafoldTask {
    executor 'nim'
    
    script:
    task.ext.nim = "alphafold2"  // or "openfold", "rfdiffusion"
    """
    echo "Running AlphaFold2 prediction"
    """
}
```

## Parameter Configuration

### RFDiffusion Parameters

```groovy
params {
    // Protein design parameters
    contigs = "A20-60/0 50-100"
    hotspot_res = ["A50", "A51", "A52", "A53", "A54"]
    diffusion_steps = 15
    
    // Input/output parameters  
    pdb_file = "input.pdb"
    output_dir = "./results"
}
```

### OpenFold Parameters

```groovy
params {
    // Folding parameters
    max_recycling_iters = 3
    early_stopping = true
    
    // Input parameters
    fasta_file = "sequence.fasta"
    msa_dir = "./msa_data"
}
```

## Complete Configuration Examples

### Production Configuration

```groovy
plugins {
    id 'nf-nim'
}

nim {
    // Production API key
    apiKey = 'nvapi-production-key'
    
    // Custom enterprise endpoints
    rfdiffusion {
        endpoint = 'https://enterprise-nim.company.com/rfdiffusion/generate'
    }
    alphafold2 {
        endpoint = 'https://enterprise-nim.company.com/alphafold2'  
    }
}

// Process resource configuration
process {
    executor = 'nim'
    
    withName: 'rfdiffusionTask' {
        ext.nim = 'rfdiffusion'
    }
    
    withName: 'alphafoldTask' {
        ext.nim = 'alphafold2'
    }
}

params {
    // Global parameters
    output_dir = "./results"
    
    // RFDiffusion parameters
    contigs = "A20-60/0 50-100"
    hotspot_res = ["A50", "A51", "A52", "A53", "A54"]
    diffusion_steps = 15
}
```

### Development Configuration

```groovy
plugins {
    id 'nf-nim'
}

nim {
    // Development endpoints
    rfdiffusion {
        endpoint = 'http://localhost:8000/biology/ipd/rfdiffusion/generate'
        apiKey = 'dev-key'
    }
    
    // Mixed development/production setup
    alphafold2 {
        endpoint = 'https://health.api.nvidia.com/v1/biology/deepmind/alphafold2'
        // Uses global API key or NVCF_RUN_KEY
    }
}

// Development parameters
params {
    // Faster development settings
    diffusion_steps = 5  // Reduced for faster testing
    max_recycling_iters = 1
}
```

### Multi-Environment Configuration

```groovy
plugins {
    id 'nf-nim'
}

profiles {
    standard {
        nim {
            // Uses NVCF_RUN_KEY environment variable
        }
    }
    
    development {
        nim {
            rfdiffusion {
                endpoint = 'http://localhost:8000/biology/ipd/rfdiffusion/generate'
                apiKey = 'dev-key'
            }
        }
    }
    
    production {
        nim {
            apiKey = 'nvapi-production-key'
            
            rfdiffusion {
                endpoint = 'https://enterprise-nim.company.com/rfdiffusion'
            }
        }
    }
}
```

## Configuration Validation

The plugin validates configuration at startup:

- **API Key Resolution**: Warns if no API key is found
- **Endpoint Connectivity**: Logs endpoint URLs being used
- **Service Mapping**: Validates service names exist

## Environment Variables

Configuration can also use environment variables:

```groovy
nim {
    apiKey = "${env.NVIDIA_API_KEY}"
    
    rfdiffusion {
        endpoint = "${env.RFDIFFUSION_ENDPOINT ?: 'https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'}"
    }
}
```

## Security Considerations

### API Key Storage

**DO NOT** commit API keys to version control:

```groovy
// ❌ Bad - hardcoded in config
nim {
    apiKey = 'nvapi-secret-key'
}

// ✅ Good - from environment
nim {
    apiKey = "${env.NVCF_RUN_KEY}"
}

// ✅ Good - from external file  
nim {
    apiKey = "${file('~/.nvidia/api_key').text.trim()}"
}
```

### Network Security

For production deployments:

- Use HTTPS endpoints only
- Configure proper SSL certificates
- Use VPN or private networks when possible
- Implement network access controls

## Troubleshooting Configuration

### Common Issues

1. **API Key Not Found**: Check resolution order and environment variables
2. **Service Not Found**: Verify service name matches configuration
3. **Endpoint Unreachable**: Check network connectivity and endpoint URLs

### Debug Configuration

Enable debug logging:

```bash
export NXF_DEBUG=1
nextflow run your-pipeline.nf
```

### Validate Configuration

Test your configuration:

```bash
# Test with dry-run
nextflow run your-pipeline.nf -preview

# Test specific service
nextflow run examples/rfdiffusion_example.nf -c your-config.config
```