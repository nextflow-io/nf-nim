# Authentication Guide

This guide covers the various methods for authenticating with NVIDIA NIM services when using the nf-nim plugin.

## Overview

The nf-nim plugin supports multiple authentication methods with a clear priority hierarchy to provide flexibility for different deployment scenarios.

## Authentication Priority

API keys are resolved in the following order (first found wins):

1. **Service-specific configuration** (`nim.service.apiKey`)
2. **Global configuration** (`nim.apiKey`) 
3. **Environment variable** (`NVCF_RUN_KEY`)

## Method 1: Environment Variable (Recommended)

The traditional method using environment variables:

### Setup

```bash
export NVCF_RUN_KEY="nvapi-your-api-key-here"
```

### Usage

```groovy
plugins {
    id 'nf-nim'
}

// No additional configuration needed
// Plugin will automatically use NVCF_RUN_KEY
```

### Advantages
- Works with existing NVIDIA tooling
- No configuration file changes needed
- Compatible with CI/CD systems
- Secure (not stored in code)

### Use Cases
- Development and testing
- CI/CD pipelines
- Single-user environments
- Quick prototyping

## Method 2: Global Configuration

Set a global API key in your Nextflow configuration:

### Setup

```groovy
plugins {
    id 'nf-nim'
}

nim {
    apiKey = 'nvapi-your-global-key'
}
```

### Usage with External Files

For security, reference external files:

```groovy
nim {
    apiKey = "${file('~/.nvidia/api_key').text.trim()}"
}
```

Or use environment variables in config:

```groovy
nim {
    apiKey = "${env.NVIDIA_API_KEY}"
}
```

### Advantages
- Centralized configuration
- Can be parameterized
- Works with Nextflow profiles
- Version controlled (if desired)

### Use Cases
- Team environments
- Multiple projects with same key
- Standardized deployments

## Method 3: Service-Specific Configuration

Configure different API keys for different NIM services:

### Setup

```groovy
nim {
    // Global fallback
    apiKey = 'nvapi-default-key'
    
    // Service-specific keys (override global)
    rfdiffusion {
        apiKey = 'nvapi-rfdiffusion-specific-key'
    }
    
    alphafold2 {
        apiKey = 'nvapi-alphafold2-specific-key'
    }
    
    openfold {
        apiKey = 'nvapi-openfold-specific-key'
        endpoint = 'https://custom-openfold.company.com/api'
    }
}
```

### Advantages
- Fine-grained access control
- Different keys for different services
- Mix of authentication methods
- Supports custom endpoints per service

### Use Cases
- Enterprise environments
- Different service providers
- Cost tracking per service
- Security isolation

## Obtaining API Keys

### NVIDIA Cloud Functions (NVCF)

1. **Register**: Create account at [NVIDIA NGC](https://ngc.nvidia.com/)
2. **Generate Key**: Go to Setup → Generate API Key
3. **Copy Key**: Save the key securely (starts with `nvapi-`)
4. **Test Access**: Verify with health check API

### Enterprise/Custom NIMs

For self-hosted or enterprise NIM instances:

1. **Contact Admin**: Get API key from your NIM administrator
2. **Custom Format**: Keys may have different format than NVCF
3. **Endpoint Setup**: Configure custom endpoints alongside keys

## Security Best Practices

### DO NOT Store Keys in Code

```groovy
// ❌ Bad - hardcoded secret
nim {
    apiKey = 'nvapi-12345-secret-key'
}

// ✅ Good - external reference
nim {
    apiKey = "${env.NVCF_RUN_KEY}"
}

// ✅ Good - file reference
nim {
    apiKey = "${file('~/.config/nvidia/api_key').text.trim()}"
}
```

### File Permissions

If storing keys in files:

```bash
# Create secure key file
mkdir -p ~/.config/nvidia
echo "nvapi-your-key-here" > ~/.config/nvidia/api_key
chmod 600 ~/.config/nvidia/api_key
```

### Environment Files

Use `.env` files that are excluded from version control:

```bash
# .env (add to .gitignore)
NVCF_RUN_KEY=nvapi-your-key-here
```

```groovy
// Load from .env
nim {
    apiKey = "${env.NVCF_RUN_KEY}"
}
```

### CI/CD Secrets

Store API keys as secrets in your CI/CD platform:

#### GitHub Actions
```yaml
env:
  NVCF_RUN_KEY: ${{ secrets.NVCF_RUN_KEY }}
```

#### GitLab CI
```yaml
variables:
  NVCF_RUN_KEY: $NVCF_RUN_KEY  # Set in project settings
```

## Multi-Environment Authentication

### Development vs Production

```groovy
profiles {
    development {
        nim {
            rfdiffusion {
                endpoint = 'http://localhost:8000/rfdiffusion'
                apiKey = 'dev-key-or-none'
            }
        }
    }
    
    production {
        nim {
            apiKey = "${env.NVCF_PROD_KEY}"
            
            rfdiffusion {
                endpoint = 'https://enterprise-nim.company.com/rfdiffusion'
                // Inherits global production key
            }
        }
    }
}
```

Usage:
```bash
# Development
nextflow run pipeline.nf -profile development

# Production  
NVCF_PROD_KEY="nvapi-prod-key" nextflow run pipeline.nf -profile production
```

### Team Configuration

```groovy
nim {
    // Team default
    apiKey = "${env.TEAM_NVIDIA_KEY}"
    
    // Individual overrides possible
    rfdiffusion {
        apiKey = "${env.USER_RFDIFFUSION_KEY ?: env.TEAM_NVIDIA_KEY}"
    }
}
```

## Troubleshooting Authentication

### Common Error Messages

#### "No API key found"
```
No API key found for service 'rfdiffusion'. 
Configure nim.apiKey or nim.rfdiffusion.apiKey, or set NVCF_RUN_KEY environment variable.
```

**Solutions:**
1. Set `NVCF_RUN_KEY` environment variable
2. Add `nim.apiKey` to configuration
3. Add service-specific `nim.service.apiKey`

#### "HTTP 401 Unauthorized"
```
Request failed with status: 401 - Authentication failed
```

**Solutions:**
1. Verify API key is correct
2. Check key has not expired
3. Ensure key has access to the service
4. Test with NVIDIA health check API

#### "HTTP 403 Forbidden"
```
Request failed with status: 403 - Access denied
```

**Solutions:**
1. Verify service access permissions
2. Check if key is for correct environment
3. Contact NVIDIA support for access issues

### Testing Authentication

#### Test Environment Variable
```bash
# Test NVCF_RUN_KEY is set
echo $NVCF_RUN_KEY

# Test API access
curl -H "Authorization: Bearer $NVCF_RUN_KEY" \
     https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate
```

#### Test Configuration
```bash
# Preview configuration
nextflow run pipeline.nf -preview

# Debug mode
NXF_DEBUG=1 nextflow run pipeline.nf
```

#### Validate Key Format
NVIDIA Cloud Functions keys typically:
- Start with `nvapi-`  
- Are 64+ characters long
- Contain alphanumeric characters and dashes

## Authentication Examples

### Basic Development Setup
```bash
export NVCF_RUN_KEY="nvapi-dev-key-here"
nextflow run examples/rfdiffusion_example.nf
```

### Production with Configuration
```groovy
// nextflow.config
plugins { id 'nf-nim' }

nim {
    apiKey = "${env.NVIDIA_PROD_KEY}"
}
```

### Mixed Authentication
```groovy
nim {
    // Development uses environment
    // Production services use config
    
    rfdiffusion {
        apiKey = "${env.NODE_ENV == 'production' ? env.RFDIFFUSION_PROD_KEY : env.NVCF_RUN_KEY}"
    }
    
    alphafold2 {
        // Always use team key
        apiKey = "${env.TEAM_ALPHAFOLD_KEY}"
    }
}
```

This flexible authentication system ensures the nf-nim plugin can adapt to various deployment scenarios while maintaining security best practices.