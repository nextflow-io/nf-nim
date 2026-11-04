# Installation Guide

This guide walks you through installing and setting up the nf-nim plugin for Nextflow.

## Prerequisites

- **Nextflow**: Version 24.10.0 or later
- **Java**: Version 11 or later (required by Nextflow)
- **NVIDIA API Access**: Valid NVCF run key for NVIDIA NIM services

## Installation Methods

### Method 1: Plugin Configuration (Recommended)

Add the plugin to your `nextflow.config` file:

```groovy
plugins {
    id 'nf-nim'
}
```

Nextflow will automatically download and install the plugin when you run your pipeline.

### Method 2: Direct Plugin Reference

Use the plugin directly in your pipeline script:

```groovy
plugins {
    id 'nf-nim@0.1.0'  // Replace with desired version
}
```

### Method 3: Development Installation

For development or testing the latest version:

```bash
# Clone the repository
git clone https://github.com/seqeralabs/nf-nim.git
cd nf-nim

# Build and install locally
make install
# OR
./gradlew installPlugin
```

## Verifying Installation

Test that the plugin is properly installed:

```bash
# Create a simple test workflow
echo 'plugins { id "nf-nim" }
process test { 
  executor "nim"
  script: "echo test"
}' > test.nf

# Run with development mode
NXF_PLUGINS_MODE=dev nextflow run test.nf
```

## Next Steps

1. **Configure Authentication**: Set up your NVIDIA API key - see [Authentication Guide](authentication.md)
2. **Basic Configuration**: Learn about configuration options - see [Configuration Guide](configuration.md)
3. **Try Examples**: Run sample workflows - see [Examples Guide](examples.md)

## Troubleshooting

### Plugin Not Found

If you encounter "Plugin 'nf-nim' not found" errors:

1. Check your Nextflow version: `nextflow -version`
2. Ensure you have internet connectivity for plugin download
3. Try clearing the plugin cache: `rm -rf ~/.nextflow/plugins`

### Version Conflicts

If you encounter version conflicts:

1. Specify an exact version: `id 'nf-nim@0.1.0'`
2. Clear Nextflow's work directory: `nextflow clean -f`
3. Update Nextflow: `nextflow self-update`

### Development Mode Issues

When using development mode (`NXF_PLUGINS_MODE=dev`):

1. Ensure the plugin is built: `./gradlew assemble`
2. Check plugin is installed: `ls ~/.nextflow/plugins/`
3. Verify development directory: `echo $NXF_PLUGINS_DEV`

## System Requirements

### Minimum Requirements
- RAM: 2GB available memory
- Disk: 100MB free space for plugin and cache
- Network: Internet access for NVIDIA API calls

### Recommended Requirements
- RAM: 4GB or more for large PDB processing
- Disk: 1GB for workflow outputs and cache
- Network: Stable broadband connection

## Environment Variables

The following environment variables affect plugin installation:

- `NXF_PLUGINS_DIR`: Plugin installation directory (default: `~/.nextflow/plugins`)
- `NXF_PLUGINS_MODE`: Set to `dev` for development mode
- `NXF_PLUGINS_DEV`: Development plugin directory

## Support

For installation issues:

1. Check the [Troubleshooting Guide](troubleshooting.md)
2. Review [Nextflow Plugin Documentation](https://www.nextflow.io/docs/latest/plugins.html)
3. Submit issues on [GitHub](https://github.com/seqeralabs/nf-nim/issues)