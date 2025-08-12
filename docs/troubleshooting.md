# Troubleshooting Guide

This guide helps you diagnose and resolve common issues when using the nf-nim plugin.

## Common Issues

### Authentication Problems

#### "No API key found for service"

**Error Message:**
```
No API key found for service 'rfdiffusion'. 
Configure nim.apiKey or nim.rfdiffusion.apiKey, or set NVCF_RUN_KEY environment variable.
```

**Causes & Solutions:**

1. **Missing API key entirely**
   ```bash
   # Check if environment variable is set
   echo $NVCF_RUN_KEY
   
   # If empty, set it
   export NVCF_RUN_KEY="your-api-key-here"
   ```

2. **Configuration not loaded**
   ```groovy
   // In nextflow.config
   nim {
       apiKey = "your-api-key"
   }
   ```

3. **Service-specific key missing**
   ```groovy
   nim {
       rfdiffusion {
           apiKey = "service-specific-key"
       }
   }
   ```

#### "HTTP 401 Unauthorized"

**Error Message:**
```
Request failed with status: 401 - Authentication failed
```

**Solutions:**

1. **Verify API key format**
   - NVIDIA keys typically start with `nvapi-`
   - Check for extra spaces or newlines
   ```bash
   echo "[$NVCF_RUN_KEY]"  # Check for whitespace
   ```

2. **Test key manually**
   ```bash
   curl -H "Authorization: Bearer $NVCF_RUN_KEY" \
        https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate
   ```

3. **Check key expiration**
   - Log into NVIDIA NGC to verify key status
   - Generate new key if expired

#### "HTTP 403 Forbidden"

**Error Message:**
```
Request failed with status: 403 - Access denied
```

**Solutions:**

1. **Verify service access**
   - Check if your account has access to the specific NIM service
   - Contact NVIDIA support for access issues

2. **Check rate limits**
   - You may have exceeded API rate limits
   - Wait and retry, or implement rate limiting

### Configuration Issues

#### "Plugin 'nf-nim' not found"

**Causes & Solutions:**

1. **Plugin not installed**
   ```bash
   # Check Nextflow version (needs 24.10.0+)
   nextflow -version
   
   # Clear plugin cache and retry
   rm -rf ~/.nextflow/plugins
   nextflow run your-pipeline.nf
   ```

2. **Network connectivity**
   ```bash
   # Test internet connection
   curl -I https://plugins.nextflow.io
   
   # Check proxy settings if behind corporate firewall
   ```

3. **Development mode issues**
   ```bash
   # Ensure plugin is built
   ./gradlew assemble
   
   # Check installation
   ls ~/.nextflow/plugins/
   
   # Verify development mode
   export NXF_PLUGINS_MODE=dev
   ```

#### "No endpoint configured for service"

**Error Message:**
```
No endpoint configured for service: custom_service
```

**Solution:**
```groovy
// Add endpoint configuration
nim {
    custom_service {
        endpoint = 'https://your-custom-endpoint.com/api'
        apiKey = 'your-key'
    }
}
```

### Runtime Issues

#### Task Hangs or Times Out

**Symptoms:**
- Process appears to run indefinitely
- No progress updates
- Eventually times out

**Solutions:**

1. **Check network connectivity**
   ```bash
   # Test endpoint connectivity
   curl -I https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate
   ```

2. **Enable debug logging**
   ```bash
   NXF_DEBUG=1 nextflow run your-pipeline.nf
   ```

3. **Check task status**
   ```groovy
   // Add timeout to process
   process myNIMProcess {
       executor 'nim'
       time '30m'  // Set reasonable timeout
       
       // ... rest of process
   }
   ```

4. **Verify input data**
   ```bash
   # Check PDB file format
   head -10 your_input.pdb
   grep "^ATOM" your_input.pdb | head -5
   ```

#### "Connection refused" or "Connection timeout"

**Solutions:**

1. **Check endpoint URL**
   ```groovy
   nim {
       rfdiffusion {
           endpoint = 'https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'  // Correct URL
       }
   }
   ```

2. **Test connectivity**
   ```bash
   # Test basic connectivity
   telnet health.api.nvidia.com 443
   
   # Or use curl
   curl -v https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate
   ```

3. **Check firewall settings**
   - Ensure HTTPS (port 443) is allowed
   - Configure proxy if behind corporate firewall

### Data Issues

#### "Invalid PDB format" or Processing Errors

**Solutions:**

1. **Validate PDB format**
   ```bash
   # Check file structure
   head -20 your_file.pdb
   
   # Verify ATOM records exist
   grep -c "^ATOM" your_file.pdb
   
   # Check file size
   wc -c your_file.pdb
   ```

2. **Clean PDB data**
   ```python
   # Example PDB cleaning script
   def clean_pdb(input_file, output_file):
       with open(input_file) as f:
           lines = f.readlines()
       
       # Keep only ATOM and HETATM records
       cleaned = [line for line in lines if line.startswith(('ATOM', 'HETATM'))]
       
       with open(output_file, 'w') as f:
           f.writelines(cleaned)
   ```

3. **Check file encoding**
   ```bash
   # Check encoding
   file your_file.pdb
   
   # Convert if needed
   iconv -f ISO-8859-1 -t UTF-8 input.pdb > output.pdb
   ```

### Performance Issues

#### Slow Processing

**Solutions:**

1. **Check input size**
   ```bash
   # Large files may take longer
   wc -l your_input.pdb
   ```

2. **Optimize parameters**
   ```groovy
   params {
       diffusion_steps = 10  // Reduce for faster processing
   }
   ```

3. **Monitor resource usage**
   ```bash
   # Check system resources
   top -p $(pgrep -f nextflow)
   ```

#### Memory Issues

**Solutions:**

1. **Increase JVM memory**
   ```bash
   export NXF_OPTS='-Xmx8g'
   nextflow run your-pipeline.nf
   ```

2. **Process smaller batches**
   ```groovy
   process batchedProcessing {
       input:
       path input_files
       
       script:
       """
       # Process files in smaller chunks
       split -l 100 ${input_files} chunk_
       """
   }
   ```

## Debugging Techniques

### Enable Debug Mode

```bash
# Full debug output
NXF_DEBUG=1 nextflow run your-pipeline.nf

# Debug specific components
NXF_DEBUG=executor,process nextflow run your-pipeline.nf
```

### Check Logs

```bash
# View execution log
cat .nextflow.log

# View task-specific logs
ls work/*/*/.command.log

# Follow real-time logs
tail -f .nextflow.log
```

### Test Individual Components

```groovy
// Minimal test workflow
#!/usr/bin/env nextflow

plugins {
    id 'nf-nim'
}

process testNIM {
    executor 'nim'
    
    output:
    path "test_result.json"
    
    script:
    task.ext.nim = "rfdiffusion"
    """
    echo "Testing NIM connectivity"
    # Use minimal test data
    """
}

workflow {
    testNIM()
}
```

### API Testing

```bash
# Test API directly
curl -X POST \
  -H "Authorization: Bearer $NVCF_RUN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"test": "data"}' \
  https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate
```

## Getting Help

### Information to Collect

When reporting issues, include:

1. **Environment information**
   ```bash
   nextflow -version
   java -version
   echo $NXF_PLUGINS_MODE
   ls ~/.nextflow/plugins/
   ```

2. **Configuration**
   ```bash
   # Sanitized config (remove API keys!)
   cat nextflow.config
   ```

3. **Error logs**
   ```bash
   # Last 50 lines of log
   tail -50 .nextflow.log
   
   # Failed task logs
   cat work/[task_hash]/.command.log
   ```

4. **Minimal reproducible example**
   - Simple workflow that reproduces the issue
   - Sample input data if possible
   - Steps to reproduce

### Support Channels

1. **GitHub Issues**: [nf-nim Issues](https://github.com/seqeralabs/nf-nim/issues)
2. **Nextflow Community**: [Nextflow Slack](https://nextflow.slack.com)
3. **NVIDIA Support**: For NVIDIA API issues

### Self-Help Resources

1. **Nextflow Documentation**: https://www.nextflow.io/docs/
2. **Plugin Development**: https://www.nextflow.io/docs/latest/plugins.html
3. **NVIDIA NIM Documentation**: https://developer.nvidia.com/nim

## Prevention Tips

### Best Practices

1. **Version pinning**
   ```groovy
   plugins {
       id 'nf-nim@0.1.0'  // Pin to specific version
   }
   ```

2. **Error handling**
   ```groovy
   process robustNIM {
       executor 'nim'
       errorStrategy 'retry'
       maxRetries 3
       
       script:
       task.ext.nim = "rfdiffusion"
       """
       # Your processing
       """
   }
   ```

3. **Input validation**
   ```groovy
   process validateInput {
       input:
       path pdb_file
       
       when:
       pdb_file.size() > 0
       
       script:
       """
       grep -q "^ATOM" ${pdb_file} || exit 1
       """
   }
   ```

4. **Monitoring**
   ```groovy
   workflow.onComplete {
       println "Workflow completed with status: ${workflow.success ? 'success' : 'failed'}"
   }
   ```

### Regular Maintenance

1. **Update regularly**
   ```bash
   # Update Nextflow
   nextflow self-update
   
   # Clear plugin cache occasionally
   rm -rf ~/.nextflow/plugins
   ```

2. **Monitor API usage**
   - Check NVIDIA NGC for usage limits
   - Monitor costs and quotas

3. **Backup configurations**
   - Version control your configs
   - Document environment setup

By following this troubleshooting guide, you should be able to resolve most common issues with the nf-nim plugin and maintain stable workflows.