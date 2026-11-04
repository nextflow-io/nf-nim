# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2025-09-10

### Added

- Process logging with dynamic output filenames
- Comprehensive testing infrastructure

### Changed

- Updated to Nextflow 25.04.0 and modern plugin architecture
- Publishing now uses new plugin registry

### Fixed

- Task lifecycle management
- Cleaner console output
- Improved logging

## [0.1.0] - 2025-08-12

### Added

- NIM executor for NVIDIA Inference Microservices
- RFDiffusion support for protein structure generation
- Multiple authentication methods
- Custom endpoint configuration
- Integration tests
- Health check utilities
- Example workflows for protein design and virtual screening
- OpenFold/AlphaFold integration examples
- Generative virtual screening workflows
- De novo protein binder design workflows

### Changed

- Authentication now uses `NVCF_RUN_KEY` environment variable

### Fixed

- Task execution stability
- Error handling improvements
- PDB file processing

[unreleased]: https://github.com/nextflow-io/nf-nim/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/nextflow-io/nf-nim/releases/tag/v0.2.0
[0.1.0]: https://github.com/nextflow-io/nf-nim/releases/tag/v0.1.0
