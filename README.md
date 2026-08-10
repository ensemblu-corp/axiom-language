
# 📜 Axiom Language

![Version](https://img.shields.io/badge/version-2.0.0-blue)
![Java](https://img.shields.io/badge/Java-26-orange)
![Depends](https://img.shields.io/badge/depends%20on-axiom--sovereign-informational)
![License](https://img.shields.io/badge/license-Limited%20Commercial-red)

**Grammar and policy enforcer for the Axiom ecosystem.**

`axiom-language` defines structural schemata and performs rigorous, deep-traversal validation of data against those policies. It is the handshake between raw parsed structures and the rest of your system.

---

## What it does

- Loads `.axiom` schema definitions
- Validates content against schema (including strict / unknown-field rejection)
- Treats validation as a secure **handshake** between data and policy
- Works on **byte[]** content or on already-parsed `PersistentMap`s

---

## Requirements

- **Java 26**
- [`axiom-sovereign`](https://github.com/ensemblu-corp/axiom-sovereign) `2.0.0` (and therefore `axiom`)

---

## Installation

**Maven**

```xml
<dependency>
    <groupId>com.ensemblu</groupId>
    <artifactId>axiom-language</artifactId>
    <version>2.0.0</version>
</dependency>
```

**Gradle**

```groovy
implementation("com.ensemblu:axiom-language:2.0.0")
```

---

## Quick start (2.0.0 API)

```java
import com.ensemblu.axiom.schema.SchemaGuard;
import com.ensemblu.axiom.sovereign.parser.AxiomDopParser;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.validation.Result;

// From raw bytes — built-in AxiomDopParser
Result<PersistentMap<String, Object>> outcome =
        SchemaGuard.checkContent(contentBytes)
                .basedOnSchemaInPath("user")           // or "user.axiom"
                .withAxiomParser();

// Custom parser function
Result<PersistentMap<String, Object>> outcome2 =
        SchemaGuard.checkContent(contentBytes)
                .basedOnSchemaInPath("user.axiom")
                .withParser(bytes -> AxiomDopParser.take(bytes).openBuffer().parse());

// From an already-parsed map (new overload — skips re-parse)
Result<PersistentMap<String, Object>> outcome3 =
        SchemaGuard.checkContent(myPersistentMap)
                .basedOnSchemaInPath("user")
                .withAxiomParser();
```

> [!IMPORTANT]
> **Breaking change from 1.0.0**  
> - `checkContent(String)` → `checkContent(byte[])`  
> - New overload: `checkContent(PersistentMap<String, Object>)`  
> - Fluent step is `basedOnSchemaInPath(...)` then `withAxiomParser()` / `withParser(...)`  
> - `withParser` expects `Function<byte[], PersistentMap<String, Object>>`

---

## Sovereign law

| Principle | Meaning |
|-----------|---------|
| **Schema-policy-oriented** | All processing is bound to an `.axiom` definition |
| **Strict mode** | `MasterGuard` can enforce complete structural enclosure (reject unknown fields) |
| **Handshake protocol** | Validation is a deliberate gate, not an afterthought |
| **Byte-first** | Schema registry loading and content checks prefer raw bytes end-to-end |

---

## Package structure

```
com.ensemblu.axiom.schema
├── SchemaGuard.java     // checkContent(byte[]) / checkContent(PersistentMap)
└── MasterGuard.java     // Deep / strict structural enforcement
```

---

## How validation flows

```text
bytes ──► SchemaGuard.checkContent(byte[])
              .basedOnSchemaInPath("…")
              .withAxiomParser() / withParser(…)
              │
              ├── loads schema (readAllBytes → AxiomDopParser)
              ├── runs handshake / MasterGuard
              └── Result<PersistentMap<String, Object>>

map ────► SchemaGuard.checkContent(PersistentMap)   // skip re-parse
              .basedOnSchemaInPath("…")
              .withAxiomParser()
```

---

## Design notes

- Schema files are loaded as `byte[]` and handed directly to `AxiomDopParser.take(...)` — no intermediate `String`.
- Prefer the `PersistentMap` overload when data is already in memory; it avoids a second parse.
- Unknown-field rejection and deep traversal live in `MasterGuard`.

---

## Related modules

| Module | Relationship |
|--------|----------------|
| `axiom-sovereign` | Supplies `AxiomDopParser` |
| `axiom` | Supplies `PersistentMap`, `Result`, `Dop` |
| `axiom-spec` | Often used after validation for CSV / JSON / SQL work |

---

## Legal

Limited Commercial License — free for evaluation, testing, and non-commercial development.  
Commercial or production use requires a paid annual contract from Ensemblu Corp.

See `LICENSE.md`. Contact: **contact@ensemblu.com**
