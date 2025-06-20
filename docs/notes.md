# How to Healthcheck

```
curl -v https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate
```

# Could we hijack the machineType directive?

So you'd be able to say what model you want to use.

```nextflow
process {
    machineType 'rfdiffusion'
    executor 'NIM'
    // ...
}
```

