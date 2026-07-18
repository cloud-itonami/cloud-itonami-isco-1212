import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";

const artifactPath = process.argv[2];
assert.ok(artifactPath, "compiled artifact path is required");
const generated = await import(pathToFileURL(artifactPath).href);
assert.match(generated.kotobaArtifact.sourceDigest, /^[0-9a-f]{64}$/);
assert.deepEqual(generated.kotobaArtifact.requiredCapabilities, []);
const api = generated.instantiateKotoba({});

for (let bits = 0; bits < 32; bits += 1) {
  const flags = Array.from({ length: 5 }, (_, index) => BigInt((bits >> index) & 1));
  const [hard, urgent, personnel, highSafety, lowConfidence] = flags;
  const expected = hard === 1n
    ? 0n
    : [urgent, personnel, highSafety, lowConfidence].some((value) => value === 1n)
      ? 1n
      : 2n;
  assert.equal(api.decision(...flags), expected, `decision mismatch for bitset ${bits}`);
}

console.log("cloud-itonami governor Kotoba pilot passed all 32 flag combinations");
