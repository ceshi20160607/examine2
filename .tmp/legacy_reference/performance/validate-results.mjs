if (process.argv[2] !== '--evidence' || !process.argv[3]) throw new Error('Summary-only validation was removed because it could produce a false PASS. Usage: node performance/validate-results.mjs --evidence EVIDENCE_DIRECTORY')
await import('./validate-evidence.mjs')
