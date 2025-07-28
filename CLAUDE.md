# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **nf-nim**, a Nextflow plugin for integrating NVIDIA NIMs (NVIDIA Inference Microservices) as custom executors for bioinformatics workflows. The plugin enables running AI-powered biological computing tasks like protein structure generation via NVIDIA's API services.

## Architecture Overview

### Core Components

**Plugin Entry Point**: `NfNIMPlugin.groovy` - Main plugin class extending `BasePlugin`

**Executor System**: 
- `NIMExecutor.groovy` - Main executor that manages NIM API endpoints and creates task handlers
- `NIMTaskHandler.groovy` - Handles individual task execution, including PDB processing and API communication
- Uses `TaskPollingMonitor` for task status monitoring with 5-second polling intervals

**Extension Points**: Registered in `build.gradle`:
- `NfNIMExtension` - Plugin functions (currently contains sample `sayHello` function)
- `NfNIMFactory` - Trace observer factory for pipeline lifecycle hooks
- `NIMExecutor` - Custom executor implementation

**Task Handler Design**: The `NIMTaskHandler` uses a modular approach separating:
1. **PDB Data Processing** - Downloads and processes PDB files from RCSB
2. **API Communication** - Makes HTTP requests to NVIDIA NIM endpoints  
3. **Result Handling** - Processes API responses and saves results

### Key Architectural Patterns

**Executor Implementation**: 
- Extends `nextflow.executor.Executor` directly (not grid-based)
- **MUST** implement `createTaskMonitor()` method when extending `Executor`
- Uses `TaskPollingMonitor.create(session, 'nim', Duration.of('5sec'))`
- Handle `Duration` import conflicts: use `import java.time.Duration as JavaDuration`

**Task Handler Communication**: 
- `NIMTaskHandler` accesses `NIMExecutor` fields (`httpClient`, `nimEndpoints`)
- These fields are package-private (not `private`) to allow access

**API Integration**:
- Default endpoint: `https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate`
- Supports custom endpoints via `nextflow.config`
- Authentication via `NVCF_RUN_KEY` environment variable
- Uses Java 11+ HTTP client with proper SSL configuration

## Development Commands

### Build Commands
```bash
# Build the plugin
make assemble
# OR: ./gradlew assemble

# Clean build artifacts
make clean

# Install plugin locally for testing
make install
# OR: ./gradlew installPlugin

# Publish plugin to registry
make release
# OR: ./gradlew releasePlugin
```

### Testing Commands
```bash
# Run all tests
make test
# OR: ./gradlew test

# Run specific test classes
./gradlew test --tests "*NIMExecutorTest*"
./gradlew test --tests "*NIMTaskHandlerTest*"
./gradlew test --tests "*NIMIntegrationTest*"
```

**Integration Test Requirements**: 
- Set `NVCF_RUN_KEY` environment variable for real API testing
- Unit tests run without external dependencies
- Integration tests require valid NVIDIA API key

### Plugin Development Environment
```bash
# Enable plugin development mode
export NXF_PLUGINS_MODE=dev

# Set plugin development directory
export NXF_PLUGINS_DEV=/path/to/plugin/projects

# Test plugin with Nextflow
nextflow run test-script.nf -plugins nf-nim@0.1.0
```

## Plugin Configuration & Usage

### Basic Configuration
```groovy
// nextflow.config
plugins {
    id 'nf-nim'
}

// Custom endpoint configuration
nim {
    rfdiffusion {
        endpoint = 'http://your-nim-server:8080/biology/ipd/rfdiffusion/generate'
    }
}
```

### Process Configuration
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

## Testing Architecture

### 3-Step Integration Testing Pattern
1. **Download PDB file** - Using test utility methods
2. **Pass data to TaskHandler** - Via `setPdbData()` method  
3. **Verify API completion** - Check response and result files

### Programmatic Usage
```groovy
// Recommended approach for testing
handler.setPdbData(pdbData)
handler.submit()

// Legacy method (downloads PDB automatically)
handler.submit()  // Downloads PDB from RCSB if no data is set
```

### Test Structure
- **`NIMExecutorTest`** - Tests executor initialization and configuration
- **`NIMTaskHandlerTest`** - Tests task handler lifecycle and error handling  
- **`NIMIntegrationTest`** - End-to-end tests with real NVIDIA API calls

## Important Implementation Notes

### Gradle Plugin Configuration
- Uses `io.nextflow.nextflow-plugin` version `0.0.1-alpha4`
- Minimum Nextflow version: `24.10.0`
- Always use Gradle wrapper: `./gradlew` not system gradle

### Extension Points Registration Pattern
```groovy
extensionPoints = [
    'seqeralabs.plugin.NfNIMExtension',     // Functions/extensions
    'seqeralabs.plugin.NfNIMFactory',       // Observer factories  
    'seqeralabs.plugin.NIMExecutor'         // Custom executors
]
```

### Code Standards
- Use `@CompileStatic` annotation on all Groovy classes
- Follow Apache License 2.0 header format
- Package structure: `seqeralabs.plugin`
- Use Spock framework for testing

### API Error Handling
- HTTP 200/202: Success
- HTTP 422: Validation error (treated as success for testing)
- Other codes: Failure with detailed error logging
- Results always saved to `work_dir/nim_result.json` for debugging

## Available Examples

- `examples/rfdiffusion_example.nf` - Basic RFDiffusion workflow
- `examples/nim_rfdiffusion_client.py` - Python client reference
- `examples/nim_rfdiffusion_client.sh` - Shell script reference
- `examples/blueprints/` - Complex workflow examples including protein design and virtual screening

## Plugin Registry

- Registry URL: `https://nf-plugins-registry.dev-tower.net/api`
- Store auth token in `~/.gradle/gradle.properties`
- Never commit credentials to version control