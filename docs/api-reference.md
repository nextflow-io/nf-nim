# API Reference

This document provides detailed reference information for NVIDIA NIM API endpoints and parameters supported by the nf-nim plugin.

## Supported NIM Services

### RFDiffusion

**Endpoint**: `https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate`

**Purpose**: Protein structure generation and design using diffusion models

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `input_pdb` | string | Yes | - | PDB structure data as string |
| `contigs` | string | No | "A20-60/0 50-100" | Contigs specification for design |
| `hotspot_res` | array[string] | No | ["A50","A51","A52","A53","A54"] | Hotspot residue specifications |
| `diffusion_steps` | integer | No | 15 | Number of diffusion steps (5-50) |

#### Example Request

```json
{
  "input_pdb": "ATOM      1  N   ALA A   1      20.154  16.967   4.339  1.00 11.99           N\n...",
  "contigs": "A20-60/0 50-100",
  "hotspot_res": ["A50", "A51", "A52", "A53", "A54"],
  "diffusion_steps": 15
}
```

#### Response Format

```json
{
  "output_pdb": "ATOM      1  N   ALA A   1      20.154  16.967   4.339  1.00 11.99           N\n...",
  "confidence_score": 0.85,
  "processing_time": 45.2,
  "metadata": {
    "model_version": "rfdiffusion-v1.2",
    "timestamp": "2025-01-01T12:00:00Z"
  }
}
```

#### Configuration Example

```groovy
nim {
    rfdiffusion {
        endpoint = 'https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'
        apiKey = 'your-api-key'
    }
}

params {
    contigs = "A20-60/0 50-100"
    hotspot_res = ["A50", "A51", "A52", "A53", "A54"]
    diffusion_steps = 15
}
```

### AlphaFold2

**Endpoint**: `https://health.api.nvidia.com/v1/biology/deepmind/alphafold2`

**Purpose**: Protein structure prediction from amino acid sequences

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `sequence` | string | Yes | - | Amino acid sequence (single letter codes) |
| `max_recycling_iters` | integer | No | 3 | Maximum recycling iterations |
| `early_stopping` | boolean | No | true | Enable early stopping |
| `msa_mode` | string | No | "auto" | MSA mode: "auto", "single", "full" |

#### Example Request

```json
{
  "sequence": "MKWVTFISLLFLFSSAYSRGVFRRDAHKSEVAHRFKDLGEENFKALVLIAFAQYLQQCPFEDHVKLVNEVTEFAKTCVAD",
  "max_recycling_iters": 3,
  "early_stopping": true,
  "msa_mode": "auto"
}
```

#### Response Format

```json
{
  "pdb_structure": "ATOM      1  N   MET A   1      20.154  16.967   4.339  1.00 95.12           N\n...",
  "confidence_scores": [95.12, 94.87, 93.45, ...],
  "plddt_scores": [92.5, 91.8, 90.2, ...],
  "metadata": {
    "model_version": "alphafold2-v2.3.1",
    "processing_time": 180.5,
    "timestamp": "2025-01-01T12:00:00Z"
  }
}
```

#### Configuration Example

```groovy
nim {
    alphafold2 {
        endpoint = 'https://health.api.nvidia.com/v1/biology/deepmind/alphafold2'
        apiKey = 'your-api-key'
    }
}

params {
    max_recycling_iters = 3
    early_stopping = true
    msa_mode = "auto"
}
```

### OpenFold

**Endpoint**: `https://health.api.nvidia.com/v1/biology/openfold`

**Purpose**: Open-source protein structure prediction

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `sequence` | string | Yes | - | Amino acid sequence |
| `model_type` | string | No | "model_1" | Model variant to use |
| `max_recycling_iters` | integer | No | 4 | Maximum recycling iterations |
| `use_precomputed_alignments` | boolean | No | false | Use precomputed MSA alignments |

#### Example Request

```json
{
  "sequence": "MKWVTFISLLFLFSSAYSRGVFRRDAHKSEVAHRFKDLGEENFKALVLIAFAQYLQQCPFEDHVKLVNEVTEFAKTCVAD",
  "model_type": "model_1",
  "max_recycling_iters": 4,
  "use_precomputed_alignments": false
}
```

#### Response Format

```json
{
  "pdb_structure": "ATOM      1  N   MET A   1      20.154  16.967   4.339  1.00 88.45           N\n...",
  "confidence_scores": [88.45, 87.92, 86.78, ...],
  "final_atom_positions": [...],
  "metadata": {
    "model_version": "openfold-v1.0.1",
    "processing_time": 120.3,
    "timestamp": "2025-01-01T12:00:00Z"
  }
}
```

## HTTP Status Codes

| Code | Meaning | Description |
|------|---------|-------------|
| 200 | OK | Request successful, result ready |
| 202 | Accepted | Request accepted, processing in progress |
| 400 | Bad Request | Invalid request parameters |
| 401 | Unauthorized | Invalid or missing API key |
| 403 | Forbidden | Access denied to service |
| 422 | Unprocessable Entity | Valid request but processing failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |
| 503 | Service Unavailable | Service temporarily unavailable |

## Error Response Format

```json
{
  "error": {
    "code": "INVALID_INPUT",
    "message": "Invalid PDB format provided",
    "details": {
      "line": 42,
      "issue": "Missing atom coordinates"
    },
    "request_id": "req_abc123",
    "timestamp": "2025-01-01T12:00:00Z"
  }
}
```

## Rate Limits

### NVIDIA Cloud Functions

| Service | Requests/Minute | Requests/Hour | Requests/Day |
|---------|----------------|---------------|--------------|
| RFDiffusion | 10 | 100 | 1000 |
| AlphaFold2 | 5 | 50 | 500 |
| OpenFold | 8 | 80 | 800 |

### Enterprise Endpoints

Rate limits for enterprise/self-hosted endpoints depend on your deployment configuration.

## Authentication

### Bearer Token Format

```http
Authorization: Bearer nvapi-your-api-key-here
```

### API Key Requirements

- Must be valid NVIDIA Cloud Functions API key
- Format: `nvapi-` followed by alphanumeric characters
- Length: typically 60+ characters
- Obtain from: [NVIDIA NGC](https://ngc.nvidia.com/)

## Plugin Integration

### Task Configuration

```groovy
process myNIMTask {
    executor 'nim'
    
    input:
    path input_file
    
    output:
    path "result.pdb"
    path "nim_result.json"
    
    script:
    task.ext.nim = "rfdiffusion"  // or "alphafold2", "openfold"
    """
    echo "Processing ${input_file}"
    """
}
```

### Parameter Passing

Parameters are automatically extracted from Nextflow `params`:

```groovy
params {
    // RFDiffusion parameters
    contigs = "A20-60/0 50-100"
    hotspot_res = ["A50", "A51", "A52", "A53", "A54"]
    diffusion_steps = 20
    
    // AlphaFold2 parameters  
    max_recycling_iters = 3
    early_stopping = true
}
```

### Output Processing

The plugin automatically:
1. Extracts the primary output (PDB structure)
2. Saves complete API response as `nim_result.json`
3. Sets appropriate exit codes based on API response

### Custom Endpoints

```groovy
nim {
    rfdiffusion {
        endpoint = 'https://custom-nim.company.com/rfdiffusion'
        apiKey = 'custom-api-key'
    }
}
```

## Development and Testing

### Health Check Endpoints

Test service availability:

```bash
# RFDiffusion
curl -H "Authorization: Bearer $NVCF_RUN_KEY" \
     https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate

# AlphaFold2  
curl -H "Authorization: Bearer $NVCF_RUN_KEY" \
     https://health.api.nvidia.com/v1/biology/deepmind/alphafold2

# OpenFold
curl -H "Authorization: Bearer $NVCF_RUN_KEY" \
     https://health.api.nvidia.com/v1/biology/openfold
```

### Local Testing

```bash
# Test with minimal data
curl -X POST \
  -H "Authorization: Bearer $NVCF_RUN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"sequence": "MKWVTFISLLFLFSSAYSRGVFRRDAHKSEVAHRFKDLGEENFKALVLIAFAQYLQQCPFEDHVKLVNEVTEFAKTCVAD"}' \
  https://health.api.nvidia.com/v1/biology/deepmind/alphafold2
```

## Advanced Configuration

### Timeout Settings

```groovy
nim {
    rfdiffusion {
        timeout = '30m'  // Request timeout
        retry_attempts = 3
        retry_delay = '5s'
    }
}
```

### SSL Configuration

```groovy
nim {
    rfdiffusion {
        endpoint = 'https://internal-nim.company.com/rfdiffusion'
        ssl_verify = false  // For self-signed certificates (not recommended)
        ca_cert_path = '/path/to/ca-cert.pem'
    }
}
```

### Proxy Configuration

```groovy
nim {
    http_proxy = 'http://proxy.company.com:8080'
    https_proxy = 'https://proxy.company.com:8080'
    no_proxy = 'localhost,127.0.0.1,.company.com'
}
```

This API reference provides the complete technical specification for integrating with NVIDIA NIM services through the nf-nim plugin.