# Nextflow NIM Plugin

A Nextflow plugin for integrating [NVIDIA NIMs (NVIDIA Inference Microservices)](https://developer.nvidia.com/nim) as custom executors for bioinformatics workflows.

## Overview

This plugin provides a generic `nim` executor that can run NVIDIA NIM services for biological computing, specifically:

- **RFDiffusion** - Protein structure generation and design

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

### Authentication

The plugin supports multiple authentication methods with flexible configuration options:

**Method 1: Environment Variable (Traditional)**

```bash
export NVCF_RUN_KEY="your-nvidia-api-key-here"
```

**Method 2: Global Configuration**

```groovy
nim {
    apiKey = 'your-api-key-here'
}
```

**Method 3: Service-Specific Configuration**

```groovy
nim {
    rfdiffusion {
        apiKey = 'service-specific-key'
    }
}
```

API keys are resolved in priority order: service-specific → global config → environment variable. See the [Authentication Guide](docs/authentication.md) for detailed setup instructions.

### Default Endpoints

- **RFDiffusion**: `https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate`

### Custom Endpoints

You can configure custom NIM endpoints in your `nextflow.config`:

```groovy
nim {
    rfdiffusion {
        endpoint = 'http://your-nim-server:8080/biology/ipd/rfdiffusion/generate'
        apiKey = 'custom-endpoint-key'  // Optional: service-specific key
    }
}
```

For complete configuration options, see the [Configuration Guide](docs/configuration.md).

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
    task.ext.nim = "rfdiffusion"

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

### Complete Workflow Example

```groovy
#!/usr/bin/env nextflow

params.pdb_file = "input.pdb"

workflow {
    // Structure-based design with RFDiffusion
    if (params.pdb_file) {
        designProtein(file(params.pdb_file))
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
```

## Input Requirements

### RFDiffusion

- **Input**: PDB file containing protein structure
- **Parameters**:
  - `params.contigs` - Contigs specification (default: "A20-60/0 50-100")
  - `params.hotspot_res` - Hotspot residues (default: ["A50","A51","A52","A53","A54"])
  - `params.diffusion_steps` - Number of diffusion steps (default: 15)

## Health Checks

Test NIM service availability:

```bash
# RFDiffusion
curl -v -H "Authorization: Bearer $NVCF_RUN_KEY" \
  https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate
```

### Example RFDiffusion Test

You can test the RFDiffusion endpoint with a sample PDB structure:

```bash
# Download a sample PDB file and test the API
curl -s https://files.rcsb.org/download/1R42.pdb | \
  grep '^ATOM' | head -n 400 | \
  awk '{printf "%s\\n", $0}' > sample_pdb.txt

# Test the API call
curl -X POST \
  -H "Authorization: Bearer $NVCF_RUN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "input_pdb": "'$(cat sample_pdb.txt | sed 's/\\n/\n/g')'",
    "contigs": "A20-60/0 50-100",
    "hotspot_res": ["A50", "A51", "A52", "A53", "A54"],
    "diffusion_steps": 15
  }' \
  https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate
```

## Architecture

### Plugin Components

The NIM plugin consists of several key components:

- **`NIMExecutor`** - Main executor that manages NIM API endpoints and creates task handlers
- **`NIMTaskHandler`** - Handles individual task execution, including PDB processing and API communication
- **`NIMIntegrationTest`** - End-to-end integration tests that validate real API functionality

### Task Handler Design

The `NIMTaskHandler` uses a modular approach that separates concerns:

1. **PDB Data Processing** - Downloads and processes PDB files from RCSB
2. **API Communication** - Makes HTTP requests to NVIDIA NIM endpoints
3. **Result Handling** - Processes API responses and saves results

For programmatic use, you can:

```groovy
// Set PDB data directly (recommended for testing)
handler.setPdbData(pdbData)
handler.submit()

// Or use legacy method (downloads PDB automatically)
handler.submit()  // Downloads PDB from RCSB if no data is set
```

### Testing Architecture

The test suite follows a 3-step integration testing pattern:

1. **Download PDB file** - Using test utility methods
2. **Pass data to TaskHandler** - Via `setPdbData()` method
3. **Verify API completion** - Check response and result files

This separation allows for:

- **Unit testing** - Mock components independently
- **Integration testing** - Test with real API endpoints
- **Isolated testing** - Test individual components without external dependencies

## Development

### Building

```bash
make assemble
```

### Testing

The test suite includes multiple levels of testing:

```bash
# Run all tests
make test

# Run specific test classes
./gradlew test --tests "*NIMExecutorTest*"
./gradlew test --tests "*NIMTaskHandlerTest*"
./gradlew test --tests "*NIMIntegrationTest*"
```

#### Test Requirements

- **Unit tests** - No external dependencies required
- **Integration tests** - Require `NVCF_RUN_KEY` environment variable for real API testing

#### Test Structure

- **`NIMExecutorTest`** - Tests executor initialization and configuration
- **`NIMTaskHandlerTest`** - Tests task handler lifecycle and error handling
- **`NIMIntegrationTest`** - End-to-end tests with real NVIDIA API calls

### Installing Locally

```bash
make install
```

### Contributing

When contributing to the plugin:

1. **Separate concerns** - Keep PDB processing, API calls, and result handling modular
2. **Test thoroughly** - Add unit tests for new functionality
3. **Document changes** - Update README and inline documentation
4. **Follow patterns** - Use the established 3-step testing pattern for integration tests

## License

This project is licensed under the Apache License 2.0 - see the [COPYING](COPYING) file for details.

## Documentation

For detailed information, see the comprehensive documentation in the [`docs/`](docs/) directory:

- **[Installation Guide](docs/installation.md)** - Setup and installation instructions
- **[Configuration Guide](docs/configuration.md)** - Configuration options and examples
- **[Authentication Guide](docs/authentication.md)** - Authentication methods and security
- **[Usage Guide](docs/usage.md)** - Usage patterns and best practices
- **[Examples Guide](docs/examples.md)** - Comprehensive workflow examples
- **[Troubleshooting Guide](docs/troubleshooting.md)** - Common issues and solutions
- **[API Reference](docs/api-reference.md)** - Complete API documentation

## Contributing

Contributions are welcome! Please see the development guidelines in the source code for more information.
