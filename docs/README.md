# nf-nim Documentation

Welcome to the comprehensive documentation for the nf-nim plugin, which enables Nextflow workflows to use NVIDIA NIM (NVIDIA Inference Microservices) for bioinformatics computing.

## Quick Start

1. **[Installation Guide](installation.md)** - Get the plugin installed and configured
2. **[Authentication Guide](authentication.md)** - Set up API keys and authentication  
3. **[Usage Guide](usage.md)** - Start using NIM services in your workflows

## Complete Documentation

### Setup & Configuration
- **[Installation Guide](installation.md)** - Installation methods and requirements
- **[Configuration Guide](configuration.md)** - Complete configuration options and examples  
- **[Authentication Guide](authentication.md)** - Authentication methods and security best practices

### Using the Plugin
- **[Usage Guide](usage.md)** - Usage patterns, workflow examples, and best practices
- **[Examples Guide](examples.md)** - Comprehensive workflow examples from basic to advanced
- **[API Reference](api-reference.md)** - Complete technical reference for all supported NIM services

### Support & Troubleshooting
- **[Troubleshooting Guide](troubleshooting.md)** - Common issues, debugging techniques, and solutions

## Supported NIM Services

The plugin currently supports these NVIDIA NIM services:

- **RFDiffusion** - Protein structure generation and design
- **AlphaFold2** - Protein structure prediction from sequences  
- **OpenFold** - Open-source protein structure prediction

## Key Features

- **Flexible Authentication** - Environment variables, global config, or service-specific API keys
- **Custom Endpoints** - Support for self-hosted and enterprise NIM instances
- **Error Handling** - Robust retry logic and detailed error reporting
- **Performance Optimized** - Efficient batch processing and concurrent execution
- **Comprehensive Testing** - Unit tests, integration tests, and example workflows

## Getting Help

- **Issues**: [GitHub Issues](https://github.com/seqeralabs/nf-nim/issues)
- **Discussions**: [Nextflow Community Slack](https://nextflow.slack.com)
- **Documentation**: This documentation covers all aspects of the plugin

## Contributing

Contributions are welcome! Please see the main project README for development guidelines and contribution instructions.

---

For the latest updates and releases, visit the [nf-nim GitHub repository](https://github.com/seqeralabs/nf-nim).